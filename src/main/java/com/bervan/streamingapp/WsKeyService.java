package com.bervan.streamingapp;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory one-time key store with TTL.
 * For production, consider Redis or other distributed store.
 */
@Component
public class WsKeyService {

    private static class KeyInfo {
        final UUID userId;
        final String roomId;
        final long expiresAtMs;

        KeyInfo(UUID userId, String roomId, long expiresAtMs) {
            this.userId = userId;
            this.roomId = roomId;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final Map<String, KeyInfo> store = new ConcurrentHashMap<>();
    private final long ttlMs = Long.parseLong(System.getProperty("ws.key.ttl.ms", "30000")); // default 30s

    public String createKey(UUID userId, String roomId) {
        String key = UUID.randomUUID().toString();
        long expires = Instant.now().toEpochMilli() + ttlMs;
        store.put(key, new KeyInfo(userId, roomId, expires));
        return key;
    }

    /**
     * Validate and consume the key. If valid, the stored entry is removed to prevent reuse.
     * @return Optional userId associated with the key when valid and matching roomId.
     */
    public Optional<UUID> validateAndConsume(String key, String roomId) {
        if (key == null || key.isBlank()) return Optional.empty();
        KeyInfo info = store.remove(key);
        if (info == null) return Optional.empty();
        if (!Objects.equals(info.roomId, roomId)) return Optional.empty();
        if (Instant.now().toEpochMilli() > info.expiresAtMs) return Optional.empty();
        return Optional.of(info.userId);
    }
}

