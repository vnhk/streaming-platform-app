package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.streamingapp.audiostreaming.model.AudioPlaylist;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AudioPlaylistExportService {
    private final AudioStorageService storageService;

    public AudioPlaylistExportService(AudioStorageService storageService) {
        this.storageService = storageService;
    }

    public StreamingResponseBody prepareZip(AudioPlaylist playlist) throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();
        StringBuilder manifest = new StringBuilder("#EXTM3U\n");
        int position = 0;
        for (var track : playlist.getTracks()) {
            File file = storageService.getTrackFile(track.getId());
            if (file == null || !file.isFile() || !file.canRead()) {
                throw new FileNotFoundException("Playlist track is unavailable: " + track.getId());
            }
            String filename = file.getName().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
            String entryName = String.format("tracks/%04d-%s", ++position, filename);
            files.put(entryName, file.toPath());
            int duration = track.getDurationSeconds() == null ? -1 : track.getDurationSeconds().intValue();
            String title = track.getTitle() == null ? filename : track.getTitle().replaceAll("[\\r\\n]", " ");
            manifest.append("#EXTINF:").append(duration).append(",").append(title).append("\n")
                    .append(entryName).append("\n");
        }
        byte[] playlistBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        return output -> {
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.setLevel(0);
                for (var entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    Files.copy(entry.getValue(), zip);
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("playlist.m3u8"));
                zip.write(playlistBytes);
                zip.closeEntry();
            }
        };
    }
}
