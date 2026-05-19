package com.bervan.streamingapp;

import com.bervan.logging.JsonLogger;

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
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    public String createKey(UUID userId, String roomId) {
        String key = UUID.randomUUID().toString();
        long expires = Instant.now().toEpochMilli() + ttlMs;
        store.put(key, new KeyInfo(userId, roomId, expires));
        log.info("WsKeyService: key created — key=" + key + " roomId=" + roomId + " userId=" + userId);
        return key;
    }

    /**
     * Validate and consume the key. If valid, the stored entry is removed to prevent reuse.
     * @return Optional userId associated with the key when valid and matching roomId.
     */
    public Optional<UUID> validateAndConsume(String key, String roomId) {
        if (key == null || key.isBlank()) {
            log.warn("WsKeyService: validateAndConsume — key is null or blank");
            return Optional.empty();
        }
        KeyInfo info = store.remove(key);
        if (info == null) {
            log.warn("WsKeyService: key not found in store (expired, consumed, or never existed) — key=" + key + " roomId=" + roomId);
            return Optional.empty();
        }
        if (!Objects.equals(info.roomId, roomId)) {
            log.warn("WsKeyService: roomId mismatch — expected=" + info.roomId + " actual=" + roomId + " key=" + key);
            return Optional.empty();
        }
        long now = Instant.now().toEpochMilli();
        if (now > info.expiresAtMs) {
            long ageMs = now - info.expiresAtMs;
            log.warn("WsKeyService: key expired — key=" + key + " roomId=" + roomId + " expiredSinceMs=" + ageMs);
            return Optional.empty();
        }
        log.info("WsKeyService: validateAndConsume — key valid, consumed. userId=" + info.userId + " roomId=" + roomId);
        return Optional.of(info.userId);
    }
}

