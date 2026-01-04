package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.config.MetadataByPathAndType;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.structure.ProductionFileType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.util.InMemoryResource;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
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

    @GetMapping(value = "/subtitles/{videoFolderId}/{language}")
    public ResponseEntity<Resource> getSubtitles(@PathVariable String videoFolderId, @PathVariable String language) {
        try {
            List<Metadata> videoFolder = videoManager.loadById(videoFolderId);

            if (videoFolder.size() != 1) {
                log.error("Could not find video file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Metadata videoFolderSingle = videoFolder.get(0);
            MetadataByPathAndType metadataByPathAndType = videoManager.loadVideoDirectoryContent(videoFolderSingle);
            List<Metadata> subtitles = metadataByPathAndType.get(videoFolderSingle.getPath() + videoFolderSingle.getFilename()).get(ProductionFileType.SUBTITLE);


            Optional<Metadata> subtitle = videoManager.getSubtitle(language, subtitles);
            if (subtitle.isPresent()) {

                Resource resource = getSubtitleResource(subtitle.get());
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

    @GetMapping("/hls/{videoFolderId}/**")
    public ResponseEntity<Resource> serveHls(HttpServletRequest request,
                                             @PathVariable String videoFolderId) throws Exception {

        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Metadata> metadata = videoManager.loadById(videoFolderId);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path baseDir = Path.of(videoManager.getSrc(metadata.get(0)));

        String fullPath = request.getRequestURI();
        String prefix = "/storage/videos/hls/" + videoFolderId + "/";

        if (!fullPath.startsWith(prefix)) {
            return ResponseEntity.badRequest().build();
        }

        String path = fullPath.substring(prefix.length());

        // Decode URL encoded characters (spaces, etc.)
        path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);

        Path file = baseDir.resolve(path).normalize();

        // Security check
        if (!file.startsWith(baseDir)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(file)) {
            log.error("HLS file not found: " + file);
            return ResponseEntity.notFound().build();
        }

        // Determine content type
        String fileName = file.toString().toLowerCase();
        String contentType;
        if (fileName.endsWith(".m3u8")) {
            contentType = "application/vnd.apple.mpegurl";
        } else if (fileName.endsWith(".ts")) {
            contentType = "video/mp2t";
        } else if (fileName.endsWith(".aac")) {
            contentType = "audio/aac";
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".m4s")) {
            contentType = "video/mp4";
        } else if (fileName.endsWith(".m4a")) {
            contentType = "audio/mp4";
        } else if (fileName.endsWith(".vtt")) {
            contentType = "text/vtt";
        } else {
            contentType = "application/octet-stream";
        }

        // Cache control
        String cacheControl = fileName.endsWith(".m3u8") ? "no-cache" : "max-age=3600";

        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .header("Access-Control-Allow-Origin", "*")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new UrlResource(file.toUri()));
    }

    @GetMapping("/video-folder/{videoFolderId}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable String videoFolderId,
            @RequestHeader(value = "Range", required = false) String httpRangeList
    ) throws IOException {
        // Security check using AuthService
        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Metadata> videoFolder = videoManager.loadById(videoFolderId);

        if (videoFolder.size() != 1) {
            log.error("Could not find file based on provided id!");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            Metadata videoFolderSingle = videoFolder.get(0);
            MetadataByPathAndType metadataByPathAndType = videoManager.loadVideoDirectoryContent(videoFolderSingle);
            List<Metadata> video = metadataByPathAndType.get(videoFolderSingle.getPath() + videoFolderSingle.getFilename()).get(ProductionFileType.VIDEO);
            Path file = Path.of(videoManager.getSrc(video.get(0)));
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
                rangeLength = Math.min(5_000_000, end - start + 1);
                region = new ResourceRegion(videoResource, start, rangeLength);
            }

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaTypeFactory
                            .getMediaType(videoResource)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .header("Accept-Ranges", "bytes")
                    .contentLength(rangeLength)
                    .body(region);
        } catch (Exception e) {
            log.error("Error! ", e);
            return ResponseEntity.badRequest().build();
        }
    }
}