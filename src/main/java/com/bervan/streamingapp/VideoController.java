package com.bervan.streamingapp;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
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
import java.util.Optional;

@RestController
@RequestMapping("/storage/videos")
public class VideoController {

    private final VideoManager videoManager;
    private final BervanLogger logger;

    public VideoController(VideoManager videoManager, BervanLogger logger) {
        this.videoManager = videoManager;
        this.logger = logger;
    }

    @GetMapping("/poster/{folderId}")
    public ResponseEntity<Resource> servePoster(@PathVariable String folderId) {
        try {
            List<Metadata> metadata = videoManager.loadById(folderId);

            if (metadata.size() != 1) {
                logger.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            List<Metadata> poster = videoManager.loadVideoDirectoryContent(metadata.get(0)).get("POSTER");
            if (poster == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            Path file = Path.of(videoManager.getSrc(poster.get(0)));
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(resource)
                            .orElse(MediaType.IMAGE_PNG))
                    .body(resource);
        } catch (Exception e) {
            logger.error(e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/subtitles/{videoId}/{language}")
    public ResponseEntity<Resource> getSubtitles(@PathVariable String videoId, @PathVariable String language) {
        try {
            List<Metadata> metadata = videoManager.loadById(videoId);

            if (metadata.size() != 1) {
                logger.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Metadata videoFolder = videoManager.getVideoFolder(metadata.get(0));
            List<Metadata> subtitles = videoManager.loadVideoDirectoryContent(videoFolder).get("SUBTITLES");
            if (subtitles == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Optional<Metadata> subtitle = subtitles.stream().filter(e -> e.getFilename().endsWith(language + "." + e.getExtension()))
                    .findFirst();
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

    @GetMapping("/video/{videoId}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable String videoId,
            @RequestHeader(value = "Range", required = false) String httpRangeList
    ) throws IOException {
        List<Metadata> metadata = videoManager.loadById(videoId);

        if (metadata.size() != 1) {
            logger.error("Could not find file based on provided id!");
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