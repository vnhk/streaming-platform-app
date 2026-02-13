package com.bervan.streamingapp.tv;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

public class TvPairingAdapter {

    private static final RestTemplate restTemplate = new RestTemplate();

    public static void connect(String pairCode) {
        if (pairCode == null || pairCode.isBlank()) {
            throw new RuntimeException("Please enter pairing code from TV.");
        }

        try {
            String url = "/api/tv/pair/assign";

            Map<String, String> body = new HashMap<>();
            body.put("pairCode", pairCode.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String httpBaseUrl = ServletUriComponentsBuilder //local communication
                    .fromCurrentContextPath()
                    .scheme("http")   // change scheme
                    .port(-1)         // remove port
                    .build()
                    .toUriString();

            ResponseEntity<String> response = restTemplate.postForEntity(httpBaseUrl + url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
            } else {
                String msg = response.getBody() != null ? response.getBody() : ("Error: " + response.getStatusCode());
                throw new RuntimeException("Failed to connect with TV: " + msg);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not connect TV: " + e.getMessage());
        }
    }
}

