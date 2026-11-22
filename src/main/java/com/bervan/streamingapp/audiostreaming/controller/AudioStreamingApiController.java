package com.bervan.streamingapp.audiostreaming.controller;

import com.bervan.streamingapp.audiostreaming.model.*;
import com.bervan.streamingapp.audiostreaming.service.AudioStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/audio")
public class AudioStreamingApiController {
    private final AudioStorageService storageService;

    public AudioStreamingApiController(AudioStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AudioLibrarySummary> getSummary(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.ok(storageService.getLibrarySummary(query, genre, artist, year));
    }

    @GetMapping("/albums")
    public ResponseEntity<List<AudioAlbum>> listAlbums(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) Integer year
    ) {
        AudioLibrarySummary summary = storageService.getLibrarySummary(query, genre, artist, year);
        return ResponseEntity.ok(summary.getAlbums());
    }

    @GetMapping("/albums/{albumId}")
    public ResponseEntity<AudioAlbum> getAlbum(@PathVariable String albumId) {
        return storageService.getAlbumById(albumId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/albums/{albumId}/cover")
    public ResponseEntity<Resource> getAlbumCover(@PathVariable String albumId) {
        var cachedOpt = storageService.getCachedCover(albumId);
        if (cachedOpt.isPresent()) {
            var cached = cachedOpt.get();
            return ResponseEntity.ok()
                    .contentType(cached.getMediaType())
                    .eTag(cached.getEtag())
                    .lastModified(cached.getLastModified())
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                    .body(new org.springframework.core.io.ByteArrayResource(cached.getBytes()));
        }

        File file = storageService.getCoverFile(albumId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        String fn = file.getName().toLowerCase();
        MediaType mediaType = fn.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/tracks/{trackId}/stream")
    public ResponseEntity<Resource> streamTrack(@PathVariable String trackId) throws IOException {
        File file = storageService.getTrackFile(trackId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        long contentLength = file.length();

        String filename = file.getName().toLowerCase();
        MediaType mediaType = MediaType.parseMediaType("audio/mpeg");
        if (filename.endsWith(".flac")) mediaType = MediaType.parseMediaType("audio/flac");
        else if (filename.endsWith(".wav")) mediaType = MediaType.parseMediaType("audio/wav");
        else if (filename.endsWith(".m4a")) mediaType = MediaType.parseMediaType("audio/mp4");
        else if (filename.endsWith(".ogg")) mediaType = MediaType.parseMediaType("audio/ogg");

        // Spring MVC handles Range requests for FileSystemResource, including 206 and 416.
        // Clients without Range must receive the complete resource with 200, not a truncated 206.
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(contentLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }
}
