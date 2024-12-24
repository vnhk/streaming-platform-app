package com.bervan.streamingapp;

import com.bervan.core.model.BervanLogger;
import com.bervan.filestorage.model.Metadata;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

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

            List<Metadata> poster = videoManager.loadVideoDirectory(metadata.get(0)).get("POSTER");
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

    @GetMapping("/video/{videoId}")
    public ResponseEntity<Resource> serveVideo(@PathVariable String videoId) {
        try {
            List<Metadata> metadata = videoManager.loadById(videoId);

            if (metadata.size() != 1) {
                logger.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Path file = Path.of(videoManager.getSrc(metadata.get(0)));

            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(resource)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .body(resource);
        } catch (Exception e) {
            logger.error(e);
            throw new RuntimeException(e);
        }
    }
}