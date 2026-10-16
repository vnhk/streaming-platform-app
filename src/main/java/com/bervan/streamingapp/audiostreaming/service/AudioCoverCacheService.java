package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.AudioAlbum;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory cache for audio album cover images.
 * Keeps decoded/raw cover bytes in RAM to eliminate disk I/O on every cover request.
 */
@Service
public class AudioCoverCacheService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");

    public static class CachedCover {
        private final byte[] bytes;
        private final MediaType mediaType;
        private final long lastModified;
        private final String etag;

        public CachedCover(byte[] bytes, MediaType mediaType, long lastModified) {
            this.bytes = bytes;
            this.mediaType = mediaType;
            this.lastModified = lastModified;
            this.etag = "\"" + Integer.toHexString(Arrays.hashCode(bytes)) + "\"";
        }

        public byte[] getBytes() {
            return bytes;
        }

        public MediaType getMediaType() {
            return mediaType;
        }

        public long getLastModified() {
            return lastModified;
        }

        public String getEtag() {
            return etag;
        }
    }

    private final Map<String, CachedCover> cache = new ConcurrentHashMap<>();

    public Optional<CachedCover> getCover(String albumId) {
        if (albumId == null) return Optional.empty();
        return Optional.ofNullable(cache.get(albumId));
    }

    public void putCover(String albumId, File file) {
        if (albumId == null || file == null || !file.exists()) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            MediaType mediaType = resolveMediaType(file.getName());
            cache.put(albumId, new CachedCover(bytes, mediaType, file.lastModified()));
            log.info("Cached cover in memory for album {}: {} bytes ({})", albumId, bytes.length, mediaType);
        } catch (IOException e) {
            log.warn("Could not cache cover file for album {}: {}", albumId, e.getMessage());
        }
    }

    public void putCover(String albumId, byte[] bytes, String filename) {
        if (albumId == null || bytes == null || bytes.length == 0) return;
        MediaType mediaType = resolveMediaType(filename);
        cache.put(albumId, new CachedCover(bytes, mediaType, System.currentTimeMillis()));
        log.info("Cached cover in memory for album {}: {} bytes ({})", albumId, bytes.length, mediaType);
    }

    public void evictCover(String albumId) {
        if (albumId != null) {
            cache.remove(albumId);
            log.info("Evicted cover from memory cache for album: {}", albumId);
        }
    }

    public void clear() {
        cache.clear();
        log.info("Cleared all cached covers from memory.");
    }

    public void preloadAll(Collection<AudioAlbum> albums, Function<String, File> coverFileResolver) {
        if (albums == null || albums.isEmpty()) return;
        log.info("Preloading album cover images into memory cache for {} albums...", albums.size());
        int loaded = 0;
        long totalBytes = 0;

        for (AudioAlbum album : albums) {
            if (album != null && album.isHasCover()) {
                try {
                    File file = coverFileResolver.apply(album.getId());
                    if (file != null && file.exists()) {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        MediaType mediaType = resolveMediaType(file.getName());
                        cache.put(album.getId(), new CachedCover(bytes, mediaType, file.lastModified()));
                        loaded++;
                        totalBytes += bytes.length;
                    }
                } catch (Exception e) {
                    log.warn("Failed to preload cover for album {}: {}", album.getId(), e.getMessage());
                }
            }
        }

        log.info("Finished preloading album covers: {} covers in memory, totaling {} KB.",
                loaded, totalBytes / 1024);
    }

    public int getCachedCount() {
        return cache.size();
    }

    public long getTotalBytes() {
        return cache.values().stream().mapToLong(c -> c.getBytes().length).sum();
    }

    private MediaType resolveMediaType(String filename) {
        if (filename == null) return MediaType.IMAGE_JPEG;
        String fn = filename.toLowerCase();
        if (fn.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (fn.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (fn.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_JPEG;
    }
}
