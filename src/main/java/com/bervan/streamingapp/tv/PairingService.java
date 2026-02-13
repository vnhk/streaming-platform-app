package com.bervan.streamingapp.tv;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory pairing storage between TV devices and tokens issued inside the main application.
 *
 * - TV page calls startPairing() to get a short numeric code shown on screen / QR.
 * - User enters this code in the main application (TV Pairing view).
 * - Main application calls confirmPairing(code, token) to attach a token to the TV session.
 * - TV then polls getToken(code) until the token is available.
 *
 * There is deliberately no persistence and no database access here.
 */
@Service
public class PairingService {

    private static final int CODE_LENGTH = 6;
    private static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000; // 10 minutes

    private final SecureRandom random = new SecureRandom();

    private final Map<String, String> codeToToken = new ConcurrentHashMap<>();
    private final Map<String, Long> codeExpiry = new ConcurrentHashMap<>();

    public String startPairing() {
        String code = generateCode();
        long expiresAt = System.currentTimeMillis() + DEFAULT_TTL_MILLIS;
        codeExpiry.put(code, expiresAt);
        // clear any previous token (if any) to make sure this code is fresh
        codeToToken.remove(code);
        return code;
    }

    public void confirmPairing(String code, String token) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("Pairing code expired or unknown");
        }
        codeToToken.put(code, token);
    }

    public Optional<String> getToken(String code) {
        if (!isValid(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(codeToToken.get(code));
    }

    private boolean isValid(String code) {
        Long expiry = codeExpiry.get(code);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            codeExpiry.remove(code);
            codeToToken.remove(code);
            return false;
        }
        return true;
    }

    private String generateCode() {
        // Simple numeric code, easy to type on TV remote
        int max = (int) Math.pow(10, CODE_LENGTH);
        int value = random.nextInt(max);
        return String.format("%0" + CODE_LENGTH + "d", value);
    }
}

