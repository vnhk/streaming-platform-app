package com.bervan.streamingapp.audiostreaming.controller;

import com.bervan.streamingapp.audiostreaming.model.*;
import com.bervan.streamingapp.audiostreaming.service.AudioStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
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
        File file = storageService.getCoverFile(albumId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        String fn = file.getName().toLowerCase();
        MediaType mediaType = fn.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/tracks/{trackId}/stream")
    public ResponseEntity<ResourceRegion> streamTrack(
            @PathVariable String trackId,
            @RequestHeader(value = "Range", required = false) String httpRangeList
    ) throws IOException {
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

        if (httpRangeList != null && !httpRangeList.isBlank()) {
            List<HttpRange> ranges = HttpRange.parseRanges(httpRangeList);
            if (!ranges.isEmpty()) {
                HttpRange range = ranges.get(0);
                long start = range.getRangeStart(contentLength);
                long end = range.getRangeEnd(contentLength);
                long rangeLength = Math.min(1024 * 1024L, end - start + 1); // 1MB chunks

                ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .contentType(mediaType)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(region);
            }
        }

        long rangeLength = Math.min(1024 * 1024L, contentLength);
        ResourceRegion region = new ResourceRegion(resource, 0, rangeLength);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }
}
