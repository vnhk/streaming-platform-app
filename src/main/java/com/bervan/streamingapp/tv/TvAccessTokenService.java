package com.bervan.streamingapp.tv;

import com.bervan.common.service.AuthService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory access token storage for TV clients.
 *
 * - Tokens are opaque random UUID strings.
 * - Each token is bound to a user id and an expiry time.
 * - Main app issues tokens, TV app only stores and forwards them.
 */
@Service
public class TvAccessTokenService {

    private static final long DEFAULT_TTL_SECONDS = 60 * 60; // 1 hour

    private static class Entry {
        final UUID userId;
        final Instant expiresAt;

        Entry(UUID userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> tokenStore = new ConcurrentHashMap<>();

    /**
     * Create a token for currently logged in user.
     */
    public String createTokenForCurrentUser() {
        UUID userId = AuthService.getLoggedUserId();
        if (userId == null) {
            throw new IllegalStateException("No logged in user, cannot create TV access token");
        }
        String token = UUID.randomUUID().toString();
        Instant expires = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);
        tokenStore.put(token, new Entry(userId, expires));
        return token;
    }

    /**
     * Resolve user id for a given token, if present and not expired.
     */
    public Optional<UUID> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tokenStore.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            tokenStore.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.userId);
    }
}

