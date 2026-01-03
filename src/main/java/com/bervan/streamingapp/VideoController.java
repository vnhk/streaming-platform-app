package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.config.ProductionData;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.util.InMemoryResource;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/storage/videos")
public class VideoController {
    private final VideoManager videoManager;
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final Map<String, ProductionData> streamingProductionData;

    public VideoController(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
    }

    @GetMapping("/poster/{folderId}")
    public ResponseEntity<Resource> servePoster(@PathVariable String folderId) {
        try {
            List<Metadata> metadataL = videoManager.loadById(folderId);

            if (metadataL.size() != 1) {
                log.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Optional<Metadata> poster = videoManager.findMp4PosterByFolderId(folderId, streamingProductionData);

            if (poster.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            Path file = Path.of(videoManager.getSrc(poster.get()));
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok().contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_PNG)).body(resource);
        } catch (Exception e) {
            log.error("Failed to load poster", e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/poster/direct/{metadataId}")
    public ResponseEntity<Resource> servePosterDirectly(@PathVariable String metadataId) {
        try {
            List<Metadata> metadata = videoManager.loadById(metadataId);

            if (metadata.size() != 1) {
                log.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Metadata m = metadata.get(0);
            if (!(m.getFilename().endsWith(".jpg") || m.getFilename().endsWith(".png"))) {
                log.error("File is not jpg or png based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Path file = Path.of(videoManager.getSrc(m));
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok().contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_PNG)).body(resource);
        } catch (Exception e) {
            log.error("Failed to load poster", e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/subtitles/{videoId}/{language}")
    public ResponseEntity<Resource> getSubtitles(@PathVariable String videoId, @PathVariable String language) {
        try {
            List<Metadata> metadata = videoManager.loadById(videoId);

            if (metadata.size() != 1) {
                log.error("Could not find video file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }


            Map<String, Metadata> subtitlesByVideoId = null;
//                    videoManager.findMp4SubtitlesByVideoId(videoId, streamingProductionData);
            log.error("getSubtitles -> Not supported yet!");
            if (subtitlesByVideoId == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Metadata subtitle = subtitlesByVideoId.get(language);
            if (subtitle != null) {

                Resource resource = getSubtitleResource(subtitle);
                if (resource.exists() && resource.isReadable()) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.valueOf("text/vtt"));

                    return ResponseEntity
                            .ok()
                            .headers(headers)
                            .body(resource);
                } else {
                    return ResponseEntity.notFound().build();
                }
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Error! ", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Resource getSubtitleResource(Metadata subtitle) throws IOException {
        if (subtitle.getExtension().equals("vtt")) {
            Path subtitlesPath = Path.of(videoManager.getSrc(subtitle));
            return new UrlResource(subtitlesPath.toUri());
        } else if (subtitle.getExtension().equals("srt")) {
            return new InMemoryResource(videoManager.convertSrtToVtt(subtitle));
        }

        throw new RuntimeException(subtitle.getExtension() + " is not supported for subtitles!");
    }

    @GetMapping("/hls/{videoId}/{filename:.+}")
    public ResponseEntity<Resource> serveHlsContent(
            @PathVariable String videoId,
            @PathVariable String filename) {
        try {
            // Security check using AuthService
            if (AuthService.getLoggedUserId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            List<Metadata> metadata = videoManager.loadById(videoId);
            if (metadata.size() != 1) {
                log.warn("Video not found for HLS request: " + videoId);
                return ResponseEntity.notFound().build();
            }

            // PATH RESOLUTION
            Path videoPath = Path.of(videoManager.getSrc(metadata.get(0)));
            Path file = videoPath.resolve(filename);

            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                if (!resource.exists() || !resource.isReadable()) {
                    log.warn("HLS file not found: " + file);
                    return ResponseEntity.notFound().build();
                }
            }

            String contentType = "application/octet-stream";
            if (filename.endsWith(".m3u8")) {
                contentType = "application/vnd.apple.mpegurl";
            } else if (filename.endsWith(".ts")) {
                contentType = "video/mp2t";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // Cache segments (ts) for performance, but not playlist (m3u8) as it might verify auth tokens or change
                    .header("Cache-Control", filename.endsWith(".m3u8") ? "no-cache" : "max-age=3600")
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to serve HLS content", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable String videoId,
            @RequestHeader(value = "Range", required = false) String httpRangeList
    ) throws IOException {
        // Security check using AuthService
        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Metadata> metadata = videoManager.loadById(videoId);

        if (metadata.size() != 1) {
            log.error("Could not find file based on provided id!");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Path file = Path.of(videoManager.getSrc(metadata.get(0)));
        FileSystemResource videoResource = new FileSystemResource(file);

        long contentLength = videoResource.contentLength();
        ResourceRegion region;
        long rangeLength = 0;
        long start = 0, end;

        if (httpRangeList == null) {
            region = new ResourceRegion(videoResource, 0, contentLength);
        } else {
            String[] ranges = httpRangeList.replace("bytes=", "").split("-");
            start = Long.parseLong(ranges[0]);
            end = ranges.length > 1 ? Long.parseLong(ranges[1]) : contentLength - 1;
            rangeLength = Math.min(1_000_000, end - start + 1); // np. 1MB fragment
            region = new ResourceRegion(videoResource, start, rangeLength);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory
                        .getMediaType(videoResource)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header("Accept-Ranges", "bytes")
                .contentLength(rangeLength)
                .body(region);
    }
}