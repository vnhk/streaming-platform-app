package com.bervan.streamingapp.audiostreaming.controller;

import com.bervan.streamingapp.audiostreaming.model.AudioPlaylist;
import com.bervan.streamingapp.audiostreaming.model.SavePlaylistRequest;
import com.bervan.streamingapp.audiostreaming.service.AudioPlaylistService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/audio/playlists")
public class AudioPlaylistApiController {
    private final AudioPlaylistService playlistService;

    public AudioPlaylistApiController(AudioPlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping
    public ResponseEntity<List<AudioPlaylist>> getAllPlaylists() {
        return ResponseEntity.ok(playlistService.getAllPlaylists());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AudioPlaylist> getPlaylistById(@PathVariable String id) {
        return playlistService.getPlaylistById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AudioPlaylist> savePlaylist(@RequestBody SavePlaylistRequest request) throws IOException {
        AudioPlaylist playlist = playlistService.savePlaylist(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(playlist);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable String id) throws IOException {
        playlistService.deletePlaylist(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<byte[]> downloadPlaylistM3u8(@PathVariable String id) {
        AudioPlaylist playlist = playlistService.getPlaylistById(id).orElse(null);
        if (playlist == null) return ResponseEntity.notFound().build();

        String m3u8 = playlistService.generateM3u8(id);
        byte[] bytes = m3u8.getBytes(StandardCharsets.UTF_8);
        String filename = sanitizeFilename(playlist.getName()) + ".m3u8";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private String sanitizeFilename(String s) {
        if (s == null) return "playlist";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
