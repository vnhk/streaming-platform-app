package com.bervan.streamingapp.audiostreaming.controller;

import com.bervan.streamingapp.audiostreaming.model.AudioAlbum;
import com.bervan.streamingapp.audiostreaming.model.AudioTrack;
import com.bervan.streamingapp.audiostreaming.service.AudioExportImportService;
import com.bervan.streamingapp.audiostreaming.service.AudioStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/audio/download")
public class AudioDownloadApiController {
    private final AudioStorageService storageService;
    private final AudioExportImportService exportImportService;

    public AudioDownloadApiController(AudioStorageService storageService,
                                     AudioExportImportService exportImportService) {
        this.storageService = storageService;
        this.exportImportService = exportImportService;
    }

    @GetMapping("/tracks/{trackId}")
    public ResponseEntity<Resource> downloadTrack(@PathVariable String trackId) {
        File file = storageService.getTrackFile(trackId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }

        AudioTrack track = storageService.getTrackById(trackId).orElse(null);
        String downloadName = track != null ? track.getFilename() : file.getName();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/albums/{albumId}")
    public ResponseEntity<StreamingResponseBody> downloadAlbumZip(@PathVariable String albumId) {
        AudioAlbum album = storageService.getAlbumById(albumId).orElse(null);
        if (album == null) return ResponseEntity.notFound().build();

        String zipName = sanitizeFilename(album.getArtist() + " - " + album.getTitle()) + ".zip";

        StreamingResponseBody stream = outputStream -> {
            exportImportService.streamAlbumZip(albumId, outputStream);
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                .body(stream);
    }

    @GetMapping("/albums/{albumId}/playlist.m3u8")
    public ResponseEntity<byte[]> downloadAlbumPlaylist(@PathVariable String albumId) {
        AudioAlbum album = storageService.getAlbumById(albumId).orElse(null);
        if (album == null) return ResponseEntity.notFound().build();

        Path playlistFile = storageService.getAudioLibraryRoot().resolve(album.getFolderPath()).resolve("playlist.m3u8");
        if (!Files.exists(playlistFile)) {
            storageService.generateM3u8Playlist(album, playlistFile.getParent());
        }

        try {
            byte[] bytes = Files.readAllBytes(playlistFile);
            String playlistFilename = sanitizeFilename(album.getTitle()) + ".m3u8";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + playlistFilename + "\"")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/library")
    public ResponseEntity<StreamingResponseBody> downloadFullLibraryZip() {
        String zipName = "AudioLibrary_Full.zip";

        StreamingResponseBody stream = outputStream -> {
            exportImportService.streamLibraryZip(outputStream);
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                .body(stream);
    }

    @GetMapping("/library/playlist.m3u8")
    public ResponseEntity<byte[]> downloadMasterPlaylist() {
        String content = exportImportService.generateMasterPlaylist();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"MasterLibrary.m3u8\"")
                .body(bytes);
    }

    private String sanitizeFilename(String s) {
        if (s == null) return "download";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
