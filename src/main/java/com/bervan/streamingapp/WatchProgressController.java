package com.bervan.streamingapp;

import com.bervan.common.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/streaming", "/streaming"})
public class WatchProgressController {

    private final VideoManager videoManager;

    public WatchProgressController(VideoManager videoManager) {
        this.videoManager = videoManager;
    }

    public record WatchProgressRequest(String videoId, double currentTime) {}

    @PostMapping("/watch-progress")
    public ResponseEntity<Void> saveWatchProgress(@RequestBody WatchProgressRequest request,
                                                   @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (request.videoId() == null || request.videoId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(user.getId().toString(), request.videoId());
        videoManager.saveWatchProgress(watchDetails, request.currentTime());
        return ResponseEntity.ok().build();
    }
}
