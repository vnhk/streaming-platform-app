package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.streamingapp.audiostreaming.model.AudioPlaylist;
import com.bervan.streamingapp.audiostreaming.model.AudioTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AudioPlaylistExportServiceTest {
    @TempDir
    Path directory;

    @Test
    void exportsTracksInOrderWithUniqueNamesAndAnOfflinePlaylist() throws Exception {
        Path first = Files.createDirectories(directory.resolve("first")).resolve("song.mp3");
        Path second = Files.createDirectories(directory.resolve("second")).resolve("song.mp3");
        Files.write(first, new byte[]{1, 2, 3});
        Files.write(second, new byte[]{4, 5});
        AudioStorageService storage = mock(AudioStorageService.class);
        when(storage.getTrackFile("first")).thenReturn(first.toFile());
        when(storage.getTrackFile("second")).thenReturn(second.toFile());
        AudioTrack firstTrack = new AudioTrack();
        firstTrack.setId("first");
        firstTrack.setTitle("First");
        AudioTrack secondTrack = new AudioTrack();
        secondTrack.setId("second");
        secondTrack.setTitle("Second");
        AudioPlaylist playlist = new AudioPlaylist();
        playlist.setTracks(List.of(secondTrack, firstTrack, secondTrack));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new AudioPlaylistExportService(storage).prepareZip(playlist).writeTo(output);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertEquals(List.of("tracks/0001-song.mp3", "tracks/0002-song.mp3",
                "tracks/0003-song.mp3", "playlist.m3u8"), List.copyOf(entries.keySet()));
        assertArrayEquals(new byte[]{4, 5}, entries.get("tracks/0001-song.mp3"));
        assertArrayEquals(new byte[]{1, 2, 3}, entries.get("tracks/0002-song.mp3"));
        assertEquals("#EXTM3U\n#EXTINF:-1,Second\ntracks/0001-song.mp3\n"
                + "#EXTINF:-1,First\ntracks/0002-song.mp3\n"
                + "#EXTINF:-1,Second\ntracks/0003-song.mp3\n",
                new String(entries.get("playlist.m3u8"), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsUnavailableTracksBeforeStartingTheDownload() {
        AudioTrack track = new AudioTrack();
        track.setId("missing");
        AudioPlaylist playlist = new AudioPlaylist();
        playlist.setTracks(List.of(track));
        AudioPlaylistExportService service = new AudioPlaylistExportService(mock(AudioStorageService.class));
        assertThrows(FileNotFoundException.class, () -> service.prepareZip(playlist));
    }
}
