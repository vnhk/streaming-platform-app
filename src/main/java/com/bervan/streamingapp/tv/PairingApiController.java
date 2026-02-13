package com.bervan.streamingapp.tv;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP API for pairing TV device with the main application.
 *
 * Flow:
 * 1. TV calls POST /api/tv/pair/start -> receives {"pairCode": "123456"}
 * 2. User enters this code in the main Vaadin app (TV Pairing view).
 * 3. Backend calls PairingService.confirmPairing(code, token) from TvPairingProxyController.
 * 4. TV polls GET /api/tv/pair/token/{pairCode} until it receives {"status":"READY","token":"..."}
 *
 * The token is an opaque string defined and validated by the main application.
 * TV page only stores it in memory and uses it to call other APIs.
 */
@RestController
@RequestMapping("/api/tv/pair")
public class PairingApiController {

    private final PairingService pairingService;

    public PairingApiController(PairingService pairingService) {
        this.pairingService = pairingService;
    }

    @PostMapping("/start")
    public Map<String, String> startPairing() {
        String code = pairingService.startPairing();
        Map<String, String> response = new HashMap<>();
        response.put("pairCode", code);
        return response;
    }

    @GetMapping("/token/{pairCode}")
    public Map<String, Object> getToken(@PathVariable String pairCode) {
        Map<String, Object> response = new HashMap<>();
        pairingService.getToken(pairCode)
                .ifPresentOrElse(token -> {
                    response.put("status", "READY");
                    response.put("token", token);
                }, () -> {
                    response.put("status", "WAITING");
                });
        return response;
    }
}

