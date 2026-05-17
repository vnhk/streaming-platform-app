package com.bervan.streamingapp;

import com.bervan.common.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/streaming", "/streaming"})
public class RemoteControlController {

    private final WsKeyService wsKeyService;

    public RemoteControlController(WsKeyService wsKeyService) {
        this.wsKeyService = wsKeyService;
    }

    @PostMapping("/remote-control/key")
    public ResponseEntity<Map<String, String>> createKey(@RequestBody Map<String, String> body,
                                                         @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        String roomId = body.get("roomId");
        if (roomId == null || roomId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UUID userId = user.getId();

        String key = wsKeyService.createKey(userId, roomId);
        return ResponseEntity.ok(Collections.singletonMap("key", key));
    }
}

