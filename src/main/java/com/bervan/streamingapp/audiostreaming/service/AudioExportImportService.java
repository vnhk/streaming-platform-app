package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.filestorage.model.BervanMockMultiPartFile;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Year;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class AudioExportImportService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");
    private final AudioStorageService storageService;
    private final AudioTagService tagService;
    private final AudioAiMetadataService aiMetadataService;

    public AudioExportImportService(AudioStorageService storageService,
                                    AudioTagService tagService,
                                    AudioAiMetadataService aiMetadataService) {
        this.storageService = storageService;
        this.tagService = tagService;
        this.aiMetadataService = aiMetadataService;
    }

    public void streamAlbumZip(String albumId, OutputStream outputStream) throws IOException {
        AudioAlbum album = storageService.getAlbumById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("Album not found: " + albumId));

        Path albumDir = storageService.getAudioLibraryRoot().resolve(album.getFolderPath());
        if (!Files.exists(albumDir)) {
            throw new FileNotFoundException("Album folder does not exist on disk: " + albumDir);
        }

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            try (var paths = Files.walk(albumDir)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    String relativeName = albumDir.relativize(path).toString();
                    try {
                        ZipEntry entry = new ZipEntry(relativeName);
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.error("Error adding file to album zip: {}", relativeName, e);
                    }
                });
            }
            zos.finish();
        }
        log.info("Finished streaming ZIP for album: {}", album.getTitle());
    }

    public void streamLibraryZip(OutputStream outputStream) throws IOException {
        Path root = storageService.getAudioLibraryRoot();
        if (!Files.exists(root)) {
            throw new FileNotFoundException("Audio library root does not exist: " + root);
        }

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    String relativeName = root.relativize(path).toString();
                    try {
                        ZipEntry entry = new ZipEntry(relativeName);
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.error("Error adding file to library zip: {}", relativeName, e);
                    }
                });
            }
            zos.finish();
        }
        log.info("Finished streaming full audio library ZIP");
    }

    public String generateMasterPlaylist() {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");

        List<AudioAlbum> albums = storageService.getAllAlbums();
        for (AudioAlbum album : albums) {
            for (AudioTrack track : album.getTracks()) {
                int duration = track.getDurationSeconds() != null ? track.getDurationSeconds().intValue() : -1;
                String artist = track.getArtist() != null ? track.getArtist() : album.getArtist();
                sb.append(String.format("#EXTINF:%d,%s - %s\n", duration, artist, track.getTitle()));
                sb.append(track.getRelativePath().replace('\\', '/')).append("\n");
            }
        }
        return sb.toString();
    }

    public AudioAlbum importZip(MultipartFile zipFile, String optionalArtist, String optionalAlbumTitle) throws Exception {
        Path tempDir = Files.createTempDirectory("audio_zip_import_");
        List<Path> extractedAudioFiles = new ArrayList<>();
        Path extractedCover = null;

        try {
            // Extract ZIP
            try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }
                    String name = entry.getName();
                    // Prevent path traversal
                    Path target = tempDir.resolve(name).normalize();
                    if (!target.startsWith(tempDir)) {
                        zis.closeEntry();
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);

                    String lower = target.getFileName().toString().toLowerCase();
                    if (lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".ogg")) {
                        extractedAudioFiles.add(target);
                    } else if (lower.endsWith("cover.jpg") || lower.endsWith("folder.jpg") || lower.endsWith("cover.png") || lower.endsWith("folder.png")) {
                        if (extractedCover == null) {
                            extractedCover = target;
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (extractedAudioFiles.isEmpty()) {
                throw new IllegalArgumentException("No audio files found in the uploaded ZIP");
            }

            // Determine Artist & Album Title from tags or AI
            String artist = optionalArtist != null && !optionalArtist.isBlank() ? optionalArtist.trim() : null;
            String albumTitle = optionalAlbumTitle != null && !optionalAlbumTitle.isBlank() ? optionalAlbumTitle.trim() : null;
            Integer releaseYear = null;
            String genre = "Unknown";

            // Inspect first few files for ID3 tags
            for (Path audioPath : extractedAudioFiles) {
                AudioTagService.TagInfo info = tagService.readTags(audioPath.toFile());
                if (artist == null && info.artist() != null && !info.artist().isBlank()) artist = info.artist();
                if (albumTitle == null && info.album() != null && !info.album().isBlank()) albumTitle = info.album();
                if (releaseYear == null && info.year() != null) releaseYear = info.year();
                if (genre.equals("Unknown") && info.genre() != null && !info.genre().isBlank()) genre = info.genre();
            }

            // If still missing metadata, ask AI
            if (artist == null || albumTitle == null) {
                List<String> filenames = extractedAudioFiles.stream()
                        .map(p -> p.getFileName().toString())
                        .toList();

                AiEnrichResponse aiResp = aiMetadataService.enrichAlbum(AiEnrichRequest.builder()
                        .artist(artist)
                        .albumTitle(albumTitle)
                        .trackFilenames(filenames)
                        .build());

                if (artist == null) artist = aiResp.getArtist();
                if (albumTitle == null) albumTitle = aiResp.getAlbumTitle();
                if (releaseYear == null) releaseYear = aiResp.getReleaseYear();
                if (aiResp.getGenres() != null && !aiResp.getGenres().isEmpty()) genre = aiResp.getGenres().get(0);
            }

            if (artist == null || artist.isBlank()) artist = "Unknown Artist";
            if (albumTitle == null || albumTitle.isBlank()) albumTitle = cleanZipName(zipFile.getOriginalFilename());
            if (releaseYear == null) releaseYear = Year.now().getValue();

            // Create Album in Storage
            CreateAlbumRequest createRequest = CreateAlbumRequest.builder()
                    .artist(artist)
                    .title(albumTitle)
                    .releaseYear(releaseYear)
                    .genres(List.of(genre))
                    .artists(List.of(artist))
                    .description("Imported from ZIP: " + zipFile.getOriginalFilename())
                    .isSingle(false)
                    .build();

            AudioAlbum album = storageService.createAlbum(createRequest, null);

            // Update cover via storageService if found
            if (extractedCover != null) {
                byte[] coverBytes = Files.readAllBytes(extractedCover);
                BervanMockMultiPartFile coverMultipart = new BervanMockMultiPartFile(
                        "cover.jpg", "cover.jpg", "image/jpeg", new ByteArrayInputStream(coverBytes)
                );
                storageService.updateCover(album.getId(), coverMultipart);
            }

            // Sort audio files by name
            extractedAudioFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));

            // Add all tracks
            int trackNum = 1;
            for (Path audioPath : extractedAudioFiles) {
                AudioTagService.TagInfo info = tagService.readTags(audioPath.toFile());
                String trackTitle = info.title() != null ? info.title() : cleanFilename(audioPath.getFileName().toString());
                int num = info.trackNumber() != null ? info.trackNumber() : trackNum;

                byte[] bytes = Files.readAllBytes(audioPath);
                BervanMockMultiPartFile multipart = new BervanMockMultiPartFile(
                        audioPath.getFileName().toString(),
                        audioPath.getFileName().toString(),
                        "audio/mpeg",
                        new ByteArrayInputStream(bytes)
                );

                storageService.addTrack(album.getId(), multipart, trackTitle, artist, num);
                trackNum++;
            }

            // Refresh and return created album
            return storageService.getAlbumById(album.getId()).orElse(album);
        } finally {
            // Delete temp dir
            deleteDirectoryRecursively(tempDir);
        }
    }

    private String cleanZipName(String fn) {
        if (fn == null) return "Imported Album";
        String name = fn;
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) name = name.substring(0, lastDot);
        return name.replaceAll("[_-]", " ").trim();
    }

    private String cleanFilename(String fn) {
        int lastDot = fn.lastIndexOf('.');
        String name = lastDot > 0 ? fn.substring(0, lastDot) : fn;
        return name.replaceFirst("^\\d+\\s*[-._]\\s*", "").trim();
    }

    private void deleteDirectoryRecursively(Path path) {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {}
    }
}
