package com.bervan.streamingapp.audiostreaming.controller;

import com.bervan.streamingapp.audiostreaming.model.*;
import com.bervan.streamingapp.audiostreaming.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/audio/admin")
public class AudioAdminApiController {
    private final AudioStorageService storageService;
    private final AudioExportImportService exportImportService;
    private final AudioAiMetadataService aiMetadataService;
    private final AudioTagService tagService;

    public AudioAdminApiController(AudioStorageService storageService,
                                  AudioExportImportService exportImportService,
                                  AudioAiMetadataService aiMetadataService,
                                  AudioTagService tagService) {
        this.storageService = storageService;
        this.exportImportService = exportImportService;
        this.aiMetadataService = aiMetadataService;
        this.tagService = tagService;
    }

    @PostMapping("/reload")
    public ResponseEntity<Void> reload() {
        storageService.reloadAll();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/enrich")
    public ResponseEntity<AiEnrichResponse> enrichMetadata(@RequestBody AiEnrichRequest request) {
        AiEnrichResponse response = aiMetadataService.enrichAlbum(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/albums", consumes = "multipart/form-data")
    public ResponseEntity<AudioAlbum> createAlbum(
            @RequestParam String title,
            @RequestParam String artist,
            @RequestParam(required = false) Integer releaseYear,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean isSingle,
            @RequestParam(required = false) MultipartFile cover
    ) {
        try {
            List<String> genreList = (genres != null && !genres.isBlank())
                    ? Arrays.stream(genres.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                    : List.of("Unknown");

            CreateAlbumRequest req = CreateAlbumRequest.builder()
                    .title(title)
                    .artist(artist)
                    .artists(List.of(artist))
                    .releaseYear(releaseYear)
                    .genres(genreList)
                    .description(description)
                    .isSingle(isSingle)
                    .build();

            AudioAlbum created = storageService.createAlbum(req, cover);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/albums/{albumId}")
    public ResponseEntity<AudioAlbum> updateAlbum(
            @PathVariable String albumId,
            @RequestBody UpdateAlbumRequest request
    ) {
        try {
            AudioAlbum updated = storageService.updateAlbum(albumId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/albums/{albumId}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable String albumId) {
        try {
            storageService.deleteAlbum(albumId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/albums/{albumId}/cover", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadCover(
            @PathVariable String albumId,
            @RequestParam("cover") MultipartFile cover
    ) {
        try {
            storageService.updateCover(albumId, cover);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/albums/{albumId}/sync-tags")
    public ResponseEntity<Void> syncTags(@PathVariable String albumId) {
        try {
            storageService.synchronizeTags(albumId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/albums/{albumId}/tracks", consumes = "multipart/form-data")
    public ResponseEntity<AudioTrack> addTrackToAlbum(
            @PathVariable String albumId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) Integer trackNumber,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String genre
    ) {
        try {
            String genresInput = (genres != null && !genres.isBlank()) ? genres : genre;
            AudioTrack track = storageService.addTrack(albumId, file, title, artist, trackNumber, genresInput);
            return ResponseEntity.status(HttpStatus.CREATED).body(track);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/upload-track-auto", consumes = "multipart/form-data")
    public ResponseEntity<AudioTrack> uploadTrackAuto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) Integer trackNumber,
            @RequestParam(defaultValue = "false") boolean isSingle
    ) {
        try {
            String genresInput = (genres != null && !genres.isBlank()) ? genres : genre;
            List<String> parsedGenres = (genresInput != null && !genresInput.isBlank())
                    ? AudioTagService.parseGenres(genresInput)
                    : null;

            // Find existing album or create one
            String targetArtist = (artist != null && !artist.isBlank()) ? artist.trim() : null;
            String targetAlbum = (album != null && !album.isBlank()) ? album.trim() : null;

            // If not provided in form, read from MP3 tags
            if (targetArtist == null || targetAlbum == null) {
                java.io.File tempFile = java.io.File.createTempFile("inspect_upload_", ".mp3");
                file.transferTo(tempFile);
                AudioTagService.TagInfo info = tagService.readTags(tempFile);
                if (targetArtist == null) targetArtist = info.artist();
                if (targetAlbum == null) targetAlbum = info.album();
                if (parsedGenres == null && info.genre() != null) {
                    parsedGenres = AudioTagService.parseGenres(info.genre());
                }
                tempFile.delete();
            }

            if (targetArtist == null || targetArtist.isBlank()) targetArtist = "Unknown Artist";
            if (isSingle) targetAlbum = "Singles";
            if (targetAlbum == null || targetAlbum.isBlank()) targetAlbum = isSingle ? "Singles" : "Unknown Album";

            final String fArtist = targetArtist;
            final String fAlbum = targetAlbum;

            AudioAlbum target = storageService.getAllAlbums().stream()
                    .filter(a -> a.getArtist().equalsIgnoreCase(fArtist) && a.getTitle().equalsIgnoreCase(fAlbum))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                target = storageService.createAlbum(CreateAlbumRequest.builder()
                        .artist(targetArtist)
                        .title(targetAlbum)
                        .genres(parsedGenres != null && !parsedGenres.isEmpty() ? parsedGenres : List.of("Unknown"))
                        .isSingle(isSingle)
                        .build(), null);
            }

            AudioTrack track = storageService.addTrack(target.getId(), file, title, targetArtist, trackNumber, parsedGenres);
            return ResponseEntity.status(HttpStatus.CREATED).body(track);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/albums/{albumId}/tracks/{trackId}")
    public ResponseEntity<AudioTrack> updateTrack(
            @PathVariable String albumId,
            @PathVariable String trackId,
            @RequestBody UpdateTrackRequest request
    ) {
        try {
            AudioTrack updated = storageService.updateTrack(albumId, trackId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/albums/{albumId}/tracks/{trackId}")
    public ResponseEntity<Void> deleteTrack(
            @PathVariable String albumId,
            @PathVariable String trackId
    ) {
        try {
            storageService.deleteTrack(albumId, trackId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/upload-zip", consumes = "multipart/form-data")
    public ResponseEntity<AudioAlbum> uploadZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String albumTitle
    ) {
        try {
            AudioAlbum created = exportImportService.importZip(file, artist, albumTitle);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
