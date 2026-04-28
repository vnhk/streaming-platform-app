package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.streamingapp.config.ProductionData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/streaming/admin")
public class StreamingAdminApiController {

    private final StreamingAdminService adminService;
    private final Map<String, ProductionData> streamingProductionData;

    public StreamingAdminApiController(StreamingAdminService adminService,
                                        Map<String, ProductionData> streamingProductionData) {
        this.adminService = adminService;
        this.streamingProductionData = streamingProductionData;
    }

    private boolean isAdmin() {
        return "ROLE_USER".equals(AuthService.getUserRole());
    }

    @PostMapping("/reload-config")
    public ResponseEntity<Void> reloadConfig() {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        adminService.reloadConfig(streamingProductionData);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/productions", consumes = "multipart/form-data")
    public ResponseEntity<Void> createProduction(
            @RequestParam String name,
            @RequestParam(defaultValue = "movie") String type,
            @RequestParam(defaultValue = "mp4") String videoFormat,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Double rating,
            @RequestParam(required = false) Integer yearStart,
            @RequestParam(required = false) Integer yearEnd,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) MultipartFile poster
    ) {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().build();
        try {
            byte[] posterBytes = null;
            String posterFilename = null;
            if (poster != null && !poster.isEmpty()) {
                posterBytes = poster.getBytes();
                posterFilename = poster.getOriginalFilename();
            }
            adminService.createProduction(name, type, videoFormat, description, rating,
                    yearStart, yearEnd, country, categories, tags, posterBytes, posterFilename);
            adminService.reloadConfig(streamingProductionData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/productions/{name}/seasons")
    public ResponseEntity<Void> createSeason(
            @PathVariable String name,
            @RequestParam int seasonNumber
    ) {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            adminService.createSeason(name, seasonNumber);
            adminService.reloadConfig(streamingProductionData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/productions/{name}/episodes/mp4", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadEpisodeMP4(
            @PathVariable String name,
            @RequestParam int seasonNumber,
            @RequestParam int episodeNumber,
            @RequestParam MultipartFile file
    ) {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            adminService.addEpisodeMP4(name, seasonNumber, episodeNumber,
                    file.getInputStream(), file.getOriginalFilename());
            adminService.reloadConfig(streamingProductionData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/productions/{name}/episodes/hls", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadEpisodeHLS(
            @PathVariable String name,
            @RequestParam int seasonNumber,
            @RequestParam MultipartFile file
    ) {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            adminService.addEpisodeHLS(name, seasonNumber,
                    file.getInputStream(), file.getOriginalFilename());
            adminService.reloadConfig(streamingProductionData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/productions/{name}/movie/mp4", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadMovieMP4(
            @PathVariable String name,
            @RequestParam MultipartFile file
    ) {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            adminService.addMovieVideoMP4(name, file.getInputStream(), file.getOriginalFilename());
            adminService.reloadConfig(streamingProductionData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/productions/{name}/subtitles", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadSubtitles(
            @PathVariable String name,
            @RequestParam MultipartFile file
    ) {
        if (!isAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            adminService.addSubtitlesFromZip(name, file.getInputStream());
            adminService.reloadConfig(streamingProductionData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
