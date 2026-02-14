package com.bervan.streamingapp.tv;

import org.springframework.stereotype.Service;

@Service
public class TvPairingAdapter {

    private final TvAccessTokenService tvAccessTokenService;
    private final PairingService pairingService;

    public TvPairingAdapter(TvAccessTokenService tvAccessTokenService, PairingService pairingService) {
        this.tvAccessTokenService = tvAccessTokenService;
        this.pairingService = pairingService;
    }

    public void connect(String pairCode) {
        if (pairCode == null || pairCode.isBlank()) {
            throw new RuntimeException("Please enter pairing code from TV.");
        }

        // Create token for currently logged-in user
        String token = tvAccessTokenService.createTokenForCurrentUser();

        try {
            pairingService.confirmPairing(pairCode.trim(), token);
        } catch (Exception e) {
            throw new RuntimeException("Could not connect TV: " + e.getMessage());
        }
    }
}

