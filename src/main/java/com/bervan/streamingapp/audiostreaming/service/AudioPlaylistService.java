package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.filestorage.model.BervanMockMultiPartFile;
import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileDBStorageService;
import com.bervan.filestorage.service.FileDiskStorageService;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AudioPlaylistService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");
    private final AudioStorageService storageService;
    private final FileServiceManager fileServiceManager;
    private final FileDiskStorageService fileDiskStorageService;
    private final FileDBStorageService fileDBStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, AudioPlaylist> playlistsById = new ConcurrentHashMap<>();

    public AudioPlaylistService(AudioStorageService storageService,
                                FileServiceManager fileServiceManager,
                                FileDiskStorageService fileDiskStorageService,
                                FileDBStorageService fileDBStorageService) {
        this.storageService = storageService;
        this.fileServiceManager = fileServiceManager;
        this.fileDiskStorageService = fileDiskStorageService;
        this.fileDBStorageService = fileDBStorageService;
    }

    public String getPlaylistsStoragePath() {
        String base = storageService.getAudioRelativePath();
        if (!base.startsWith("/")) base = "/" + base;
        while (base.endsWith("/") && base.length() > 1) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/Playlists/";
    }

    public Path getPlaylistsDirectory() {
        return fileDiskStorageService.getFile(getPlaylistsStoragePath());
    }

    @PostConstruct
    public void init() {
        ensurePlaylistsDirectoryExists();
        reloadPlaylists();
    }

    private void ensurePlaylistsDirectoryExists() {
        try {
            Path dir = getPlaylistsDirectory();
            if (!Files.exists(dir)) {
                String base = storageService.getAudioRelativePath();
                if (!base.startsWith("/")) base = "/" + base;
                while (base.endsWith("/") && base.length() > 1) {
                    base = base.substring(0, base.length() - 1);
                }
                fileServiceManager.createEmptyDirectory(base + "/", "Playlists");
                log.info("Initialized Playlists directory at {}", dir);
            }
        } catch (Exception e) {
            log.warn("Could not ensure playlists directory: {}", e.getMessage());
        }
    }

    public synchronized void reloadPlaylists() {
        playlistsById.clear();
        Path dir = getPlaylistsDirectory();
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    AudioPlaylist playlist = objectMapper.readValue(json, AudioPlaylist.class);
                    if (playlist != null && playlist.getId() != null) {
                        resolvePlaylistTracks(playlist);
                        playlistsById.put(playlist.getId(), playlist);
                    }
                } catch (Exception e) {
                    log.error("Failed to load playlist file {}: {}", p, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to read playlists directory: {}", e.getMessage(), e);
        }
        log.info("Loaded {} custom playlists from disk.", playlistsById.size());
    }

    public List<AudioPlaylist> getAllPlaylists() {
        List<AudioPlaylist> list = new ArrayList<>(playlistsById.values());
        for (AudioPlaylist p : list) {
            resolvePlaylistTracks(p);
        }
        list.sort(Comparator.comparing(AudioPlaylist::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Optional<AudioPlaylist> getPlaylistById(String id) {
        AudioPlaylist playlist = playlistsById.get(id);
        if (playlist != null) {
            resolvePlaylistTracks(playlist);
            return Optional.of(playlist);
        }
        return Optional.empty();
    }

    public synchronized AudioPlaylist savePlaylist(SavePlaylistRequest request) throws IOException {
        ensurePlaylistsDirectoryExists();

        String id = (request.getId() != null && !request.getId().isBlank())
                ? request.getId().trim()
                : UUID.randomUUID().toString();

        String now = LocalDateTime.now().toString();
        AudioPlaylist existing = playlistsById.get(id);
        String createdAt = existing != null && existing.getCreatedAt() != null ? existing.getCreatedAt() : now;

        List<String> metadataIds = request.getTrackMetadataIds() != null
                ? new ArrayList<>(request.getTrackMetadataIds())
                : new ArrayList<>();

        PlaylistFilters filters = request.getFilters() != null
                ? request.getFilters()
                : new PlaylistFilters();

        AudioPlaylist playlist = AudioPlaylist.builder()
                .id(id)
                .name(request.getName() != null && !request.getName().isBlank() ? request.getName().trim() : "Untitled Playlist")
                .description(request.getDescription() != null ? request.getDescription().trim() : "")
                .createdAt(createdAt)
                .updatedAt(now)
                .filters(filters)
                .trackMetadataIds(metadataIds)
                .build();

        // Resolve tracks and compute stats
        resolvePlaylistTracks(playlist);

        // Save to disk and database via FileServiceManager
        String filename = "playlist_" + id + ".json";
        String storagePath = getPlaylistsStoragePath();
        Path dir = getPlaylistsDirectory();
        byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(playlist).getBytes(StandardCharsets.UTF_8);

        saveOrUpdatePlaylistFile(filename, jsonBytes, "Audio playlist: " + playlist.getName(), storagePath, dir);

        playlistsById.put(playlist.getId(), playlist);
        log.info("Saved playlist [{}] with {} tracks.", playlist.getName(), playlist.getTrackCount());
        return playlist;
    }

    public synchronized void deletePlaylist(String id) throws IOException {
        AudioPlaylist removed = playlistsById.remove(id);
        if (removed != null) {
            String filename = "playlist_" + id + ".json";
            String storagePath = getPlaylistsStoragePath();

            try {
                List<Metadata> found = fileDBStorageService.loadByPathAndFilename(storagePath, filename);
                for (Metadata m : found) {
                    fileServiceManager.delete(m);
                }
            } catch (Exception e) {
                log.warn("Could not delete playlist metadata for {}: {}", filename, e.getMessage());
            }

            Path dir = getPlaylistsDirectory();
            Path filePath = dir.resolve(filename);
            Files.deleteIfExists(filePath);
            log.info("Deleted playlist [{}] (id: {})", removed.getName(), id);
        }
    }

    public String generateM3u8(String id) {
        AudioPlaylist playlist = getPlaylistById(id)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found: " + id));

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#PLAYLIST:").append(playlist.getName()).append("\n");

        for (AudioTrack track : playlist.getTracks()) {
            int duration = track.getDurationSeconds() != null ? track.getDurationSeconds().intValue() : -1;
            String artist = track.getArtist() != null ? track.getArtist() : "Unknown Artist";
            sb.append(String.format("#EXTINF:%d,%s - %s\n", duration, artist, track.getTitle()));
            sb.append(track.getRelativePath().replace('\\', '/')).append("\n");
        }

        return sb.toString();
    }

    private void resolvePlaylistTracks(AudioPlaylist playlist) {
        List<AudioTrack> validTracks = new ArrayList<>();
        if (playlist.getTrackMetadataIds() != null) {
            for (String metaId : playlist.getTrackMetadataIds()) {
                AudioTrack track = storageService.getTrackByMetadataId(metaId);
                if (track != null) {
                    validTracks.add(track);
                }
            }
        }
        playlist.setTracks(validTracks);
        playlist.setTrackCount(validTracks.size());
        playlist.setTotalDurationSeconds(validTracks.stream()
                .mapToDouble(t -> t.getDurationSeconds() != null ? t.getDurationSeconds() : 0.0)
                .sum());
    }

    private void saveOrUpdatePlaylistFile(String filename, byte[] content, String description, String storagePath, Path dir) {
        Metadata existingMeta = null;
        try {
            List<Metadata> found = fileDBStorageService.loadByPathAndFilename(storagePath, filename);
            if (!found.isEmpty()) {
                existingMeta = found.get(0);
            }
        } catch (Exception e) {
            log.warn("Could not check metadata for playlist file {}: {}", filename, e.getMessage());
        }

        if (existingMeta != null) {
            Path target = dir.resolve(filename);
            fileDiskStorageService.overwriteFile(target, content);
            existingMeta.setFileSize((long) content.length);
            existingMeta.setModificationDate(LocalDateTime.now());
            fileServiceManager.updateMetadata(existingMeta);
        } else {
            try {
                Files.deleteIfExists(dir.resolve(filename));
            } catch (Exception ignored) {}

            BervanMockMultiPartFile mockFile = new BervanMockMultiPartFile(
                    filename, filename, "application/json", new ByteArrayInputStream(content)
            );
            fileServiceManager.save(mockFile, description, storagePath);
        }
    }
}
