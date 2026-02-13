package com.bervan.streamingapp.tv;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint in the main application that connects logged-in user with a TV device.
 *
 * Flow:
 * - User is logged in in the main Vaadin app.
 * - User enters pairing code from TV screen into the main app UI.
 * - Main app calls POST /api/tv/pair/assign with {"pairCode": "..."}.
 * - Controller generates an access token for current user and stores it
 *   in PairingService so that TV page can pick it up.
 */
@RestController
@RequestMapping("/api/tv/pair")
public class TvPairingProxyController {

    private final TvAccessTokenService tvAccessTokenService;
    private final PairingService pairingService;

    public TvPairingProxyController(TvAccessTokenService tvAccessTokenService,
                                    PairingService pairingService) {
        this.tvAccessTokenService = tvAccessTokenService;
        this.pairingService = pairingService;
    }

    @PostMapping(path = "/assign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> assignTokenToTv(@RequestBody Map<String, String> body) {
        String pairCode = body.get("pairCode");
        if (pairCode == null || pairCode.isBlank()) {
            return ResponseEntity.badRequest().body("pairCode is required");
        }

        // Create token for currently logged-in user
        String token = tvAccessTokenService.createTokenForCurrentUser();

        try {
            pairingService.confirmPairing(pairCode.trim(), token);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Pairing code not found or expired");
        }
    }
}
