package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.ProductionDetails;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/streaming/light-player")
public class LightPlayerController {

    private final VideoManager videoManager;
    private final Map<String, ProductionData> streamingProductionData;

    public LightPlayerController(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
    }

    @GetMapping(value = "/page", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> serveLightPlayerPage() {
        try {
            ClassPathResource resource = new ClassPathResource("static/light-player.html");
            byte[] content = resource.getInputStream().readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/video-info/{productionName}/{videoFolderId}")
    public ResponseEntity<Map<String, Object>> getVideoInfo(
            @PathVariable String productionName,
            @PathVariable String videoFolderId) {

        UUID userId = AuthService.getLoggedUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ProductionData productionData = streamingProductionData.get(productionName);
        if (productionData == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<Metadata> videoFolder = videoManager.findVideoFolderById(videoFolderId, productionData);
        if (videoFolder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProductionDetails details = productionData.getProductionDetails();
        ProductionDetails.VideoFormat format = details.getVideoFormat();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productionName", productionName);
        result.put("videoFolderId", videoFolderId);
        result.put("videoName", videoFolder.get().getFilename());
        result.put("videoFormat", format.name());

        if (format == ProductionDetails.VideoFormat.HLS) {
            result.put("videoUrl", "/storage/videos/hls/" + videoFolderId + "/master.m3u8");
        } else {
            result.put("videoUrl", "/storage/videos/video-folder/" + videoFolderId);
        }

        Set<String> availableSubtitles = videoManager.availableSubtitles(videoFolder.get());
        result.put("availableSubtitles", availableSubtitles);

        Map<String, String> subtitleUrls = new LinkedHashMap<>();
        for (String lang : availableSubtitles) {
            subtitleUrls.put(lang, "/storage/videos/subtitles/" + videoFolderId + "/" + lang);
        }
        result.put("subtitleUrls", subtitleUrls);

        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(userId.toString(), videoFolderId);
        result.put("watchProgress", watchDetails.getCurrentVideoTime());

        Optional<Metadata> nextEpisode = videoManager.getNextVideoWithCrossSeasonSupport(videoFolderId, productionData);
        nextEpisode.ifPresent(m -> result.put("nextEpisodeId", m.getId().toString()));

        Optional<Metadata> prevEpisode = videoManager.getPrevVideoWithCrossSeasonSupport(videoFolderId, productionData);
        prevEpisode.ifPresent(m -> result.put("prevEpisodeId", m.getId().toString()));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/watch-progress")
    public ResponseEntity<Void> saveWatchProgress(@RequestBody Map<String, Object> body) {
        UUID userId = AuthService.getLoggedUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String videoId = (String) body.get("videoId");
        Number currentTime = (Number) body.get("currentTime");

        if (videoId == null || currentTime == null) {
            return ResponseEntity.badRequest().build();
        }

        WatchDetails watchDetails = videoManager.getOrCreateWatchDetails(userId.toString(), videoId);
        videoManager.saveWatchProgress(watchDetails, currentTime.doubleValue());

        return ResponseEntity.ok().build();
    }
}
