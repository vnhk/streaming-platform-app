package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.filestorage.model.BervanMockMultiPartFile;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Year;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
        AudioZipImportResult result = importZip(zipFile, optionalArtist, optionalAlbumTitle, false);
        return result.getPrimaryAlbum();
    }

    public AudioZipImportResult importZip(MultipartFile zipFile, String optionalArtist, String optionalAlbumTitle, boolean autoSort) throws Exception {
        String filename = zipFile != null ? zipFile.getOriginalFilename() : "unknown.zip";
        long sizeBytes = zipFile != null ? zipFile.getSize() : 0;
        log.info("Starting ZIP import for '{}' (size: {} bytes, autoSort: {})", filename, sizeBytes, autoSort);

        Path tempDir = Files.createTempDirectory("audio_zip_import_");
        List<Path> extractedAudioFiles = new ArrayList<>();
        Map<Path, Path> coverByFolder = new HashMap<>();

        try {
            extractZipArchive(zipFile, tempDir, extractedAudioFiles, coverByFolder);

            if (extractedAudioFiles.isEmpty()) {
                log.error("No valid audio files found in uploaded ZIP: '{}'", filename);
                throw new IllegalArgumentException("No audio files found in the uploaded ZIP (supported formats: MP3, FLAC, WAV, M4A, OGG).");
            }

            // Sort audio files alphabetically by filename
            extractedAudioFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));

            if (autoSort) {
                return importZipAutoSorted(extractedAudioFiles, coverByFolder, optionalArtist, optionalAlbumTitle);
            } else {
                return importZipSingleAlbum(zipFile, extractedAudioFiles, coverByFolder, optionalArtist, optionalAlbumTitle);
            }
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    private void extractZipArchive(MultipartFile zipFile, Path tempDir, List<Path> extractedAudioFiles, Map<Path, Path> coverByFolder) throws IOException {
        Path tempZip = tempDir.resolve("uploaded.zip");
        Files.copy(zipFile.getInputStream(), tempZip, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved uploaded ZIP to temp file '{}' ({} bytes). Opening archive...", tempZip.getFileName(), Files.size(tempZip));

        Charset[] charsetsToTry = new Charset[]{
                StandardCharsets.UTF_8,
                Charset.forName("Cp437"),
                StandardCharsets.ISO_8859_1
        };

        ZipFile zip = null;
        for (Charset cs : charsetsToTry) {
            try {
                zip = new ZipFile(tempZip.toFile(), cs);
                log.info("Successfully opened ZIP archive using charset: {}", cs.name());
                break;
            } catch (Exception e) {
                log.warn("Could not open ZIP with charset {}: {}", cs.name(), e.getMessage());
            }
        }

        if (zip == null) {
            throw new IllegalArgumentException("Unable to read ZIP archive. The file may be corrupt or in an unsupported format.");
        }

        int totalEntries = 0;
        int skippedEntries = 0;

        final ZipFile zipArchive = zip;
        try (zipArchive) {
            Enumeration<? extends ZipEntry> entries = zipArchive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                totalEntries++;

                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }

                // Filter out macOS resource forks (__MACOSX, ._*)
                if (name.contains("__MACOSX") || name.startsWith("._") || name.contains("/._")) {
                    log.debug("Skipping macOS resource fork entry: {}", name);
                    skippedEntries++;
                    continue;
                }

                // Filter out hidden system files (.DS_Store, desktop.ini, Thumbs.db)
                Path namePath = Paths.get(name);
                Path entryFileName = namePath.getFileName();
                if (entryFileName != null) {
                    String fn = entryFileName.toString();
                    if (fn.startsWith(".") || "desktop.ini".equalsIgnoreCase(fn) || "thumbs.db".equalsIgnoreCase(fn)) {
                        log.debug("Skipping hidden system file: {}", name);
                        skippedEntries++;
                        continue;
                    }
                }

                // Prevent Zip Slip / path traversal
                Path target = tempDir.resolve(name).normalize();
                if (!target.startsWith(tempDir)) {
                    log.warn("Blocked potentially malicious ZIP path traversal entry: {}", name);
                    skippedEntries++;
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (InputStream is = zipArchive.getInputStream(entry)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }

                String lower = target.getFileName().toString().toLowerCase();
                if (lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav")
                        || lower.endsWith(".m4a") || lower.endsWith(".ogg")) {
                    extractedAudioFiles.add(target);
                } else if (lower.endsWith("cover.jpg") || lower.endsWith("folder.jpg")
                        || lower.endsWith("cover.png") || lower.endsWith("folder.png")
                        || lower.endsWith("cover.jpeg") || lower.endsWith("folder.jpeg")
                        || lower.endsWith("front.jpg") || lower.endsWith("front.png")) {
                    coverByFolder.putIfAbsent(target.getParent(), target);
                }
            }
        }

        log.info("ZIP extraction complete: {} entries inspected, {} skipped, {} audio track(s) found, {} cover(s) detected",
                totalEntries, skippedEntries, extractedAudioFiles.size(), coverByFolder.size());
    }

    private AudioZipImportResult importZipAutoSorted(List<Path> audioFiles, Map<Path, Path> coverByFolder,
                                                     String fallbackArtist, String fallbackAlbumTitle) {
        Map<String, AudioAlbum> targetAlbums = new HashMap<>();
        Set<String> affectedAlbumTitles = new LinkedHashSet<>();
        List<String> failedTracks = new ArrayList<>();
        int importedCount = 0;
        AudioAlbum firstAlbum = null;

        log.info("Starting auto-sort import for {} audio file(s)...", audioFiles.size());

        for (int i = 0; i < audioFiles.size(); i++) {
            Path audioPath = audioFiles.get(i);
            String rawFileName = audioPath.getFileName().toString();
            log.info("Processing track [{}/{}]: {}", i + 1, audioFiles.size(), rawFileName);

            try {
                AudioTagService.TagInfo info = tagService.readTags(audioPath.toFile());

                String artist = (info.artist() != null && !info.artist().isBlank()) ? info.artist().trim()
                        : (fallbackArtist != null && !fallbackArtist.isBlank() ? fallbackArtist.trim() : "Unknown Artist");

                String albumTitle = (info.album() != null && !info.album().isBlank()) ? info.album().trim()
                        : (fallbackAlbumTitle != null && !fallbackAlbumTitle.isBlank() ? fallbackAlbumTitle.trim() : "Singles");

                boolean isSingle = "Singles".equalsIgnoreCase(albumTitle);
                Integer releaseYear = info.year() != null ? info.year() : Year.now().getValue();
                List<String> trackGenres = AudioTagService.parseGenres(info.genre());
                if (trackGenres.isEmpty()) {
                    trackGenres = List.of("Unknown");
                }

                String cacheKey = artist.toLowerCase() + "|||" + albumTitle.toLowerCase();
                AudioAlbum album = targetAlbums.get(cacheKey);

                if (album == null) {
                    final String fArtist = artist;
                    final String fAlbum = albumTitle;
                    album = storageService.getAllAlbums().stream()
                            .filter(a -> a.getArtist().equalsIgnoreCase(fArtist) && a.getTitle().equalsIgnoreCase(fAlbum))
                            .findFirst()
                            .orElse(null);
                }

                if (album == null) {
                    log.info("Creating new album '{}' by artist '{}' (single: {})", albumTitle, artist, isSingle);
                    CreateAlbumRequest createReq = CreateAlbumRequest.builder()
                            .artist(artist)
                            .title(albumTitle)
                            .releaseYear(releaseYear)
                            .genres(trackGenres)
                            .artists(List.of(artist))
                            .description("Imported from ZIP (auto-sorted)")
                            .isSingle(isSingle)
                            .build();

                    album = storageService.createAlbum(createReq, null);

                    // Set cover if folder had one
                    Path folderCover = coverByFolder.get(audioPath.getParent());
                    if (folderCover != null && Files.exists(folderCover)) {
                        try {
                            byte[] coverBytes = Files.readAllBytes(folderCover);
                            BervanMockMultiPartFile coverMultipart = new BervanMockMultiPartFile(
                                    "cover.jpg", "cover.jpg", "image/jpeg", new ByteArrayInputStream(coverBytes)
                            );
                            storageService.updateCover(album.getId(), coverMultipart);
                            album.setHasCover(true);
                            log.info("Attached folder cover image to album '{}'", albumTitle);
                        } catch (Exception e) {
                            log.warn("Failed to set cover for album '{}': {}", albumTitle, e.getMessage());
                        }
                    }
                }

                targetAlbums.put(cacheKey, album);
                if (firstAlbum == null) firstAlbum = album;
                affectedAlbumTitles.add(artist + " - " + albumTitle);

                String trackTitle = (info.title() != null && !info.title().isBlank())
                        ? info.title().trim()
                        : cleanFilename(rawFileName);

                int trackNum = info.trackNumber() != null ? info.trackNumber() : (album.getTracks().size() + 1);

                byte[] bytes = Files.readAllBytes(audioPath);
                String mimeType = determineAudioMimeType(rawFileName);
                BervanMockMultiPartFile multipart = new BervanMockMultiPartFile(
                        rawFileName,
                        rawFileName,
                        mimeType,
                        bytes
                );

                storageService.addTrack(album.getId(), multipart, trackTitle, artist, trackNum, trackGenres);
                importedCount++;
                log.info("Successfully imported track [{}/{}]: '{}' into album '{}'", i + 1, audioFiles.size(), trackTitle, album.getTitle());
            } catch (Exception e) {
                log.error("Failed to import track '{}': {}", rawFileName, e.getMessage(), e);
                failedTracks.add(rawFileName + " (" + e.getMessage() + ")");
            }
        }

        String summaryMsg = String.format("Successfully auto-sorted %d tracks across %d album(s)", importedCount, targetAlbums.size());
        if (!failedTracks.isEmpty()) {
            summaryMsg += String.format(" (%d track(s) failed)", failedTracks.size());
        }
        log.info("Auto-sort ZIP import complete: {}", summaryMsg);

        return AudioZipImportResult.builder()
                .importedTracksCount(importedCount)
                .albumsCount(targetAlbums.size())
                .affectedAlbumTitles(new ArrayList<>(affectedAlbumTitles))
                .failedTracks(failedTracks)
                .primaryAlbum(firstAlbum)
                .message(summaryMsg)
                .build();
    }

    private AudioZipImportResult importZipSingleAlbum(MultipartFile zipFile, List<Path> extractedAudioFiles,
                                                     Map<Path, Path> coverByFolder, String optionalArtist,
                                                     String optionalAlbumTitle) throws Exception {
        log.info("Starting single-album ZIP import for {} track(s)...", extractedAudioFiles.size());

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
            log.info("Artist or album title not found in tags; requesting AI enrichment...");
            List<String> filenames = extractedAudioFiles.stream()
                    .map(p -> p.getFileName().toString())
                    .toList();

            try {
                AiEnrichResponse aiResp = aiMetadataService.enrichAlbum(AiEnrichRequest.builder()
                        .artist(artist)
                        .albumTitle(albumTitle)
                        .trackFilenames(filenames)
                        .build());

                if (artist == null) artist = aiResp.getArtist();
                if (albumTitle == null) albumTitle = aiResp.getAlbumTitle();
                if (releaseYear == null) releaseYear = aiResp.getReleaseYear();
                if (aiResp.getGenres() != null && !aiResp.getGenres().isEmpty()) genre = aiResp.getGenres().get(0);
                log.info("AI enrichment result: artist='{}', albumTitle='{}', releaseYear={}, genre='{}'",
                        artist, albumTitle, releaseYear, genre);
            } catch (Exception e) {
                log.warn("AI metadata enrichment failed: {}. Continuing with fallback values.", e.getMessage());
            }
        }

        if (artist == null || artist.isBlank()) artist = "Unknown Artist";
        if (albumTitle == null || albumTitle.isBlank()) albumTitle = cleanZipName(zipFile.getOriginalFilename());
        if (releaseYear == null) releaseYear = Year.now().getValue();

        log.info("Creating single album '{}' by artist '{}' (year: {}, genre: '{}')", albumTitle, artist, releaseYear, genre);

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
        Path extractedCover = coverByFolder.values().stream().findFirst().orElse(null);
        if (extractedCover != null) {
            try {
                byte[] coverBytes = Files.readAllBytes(extractedCover);
                BervanMockMultiPartFile coverMultipart = new BervanMockMultiPartFile(
                        "cover.jpg", "cover.jpg", "image/jpeg", new ByteArrayInputStream(coverBytes)
                );
                storageService.updateCover(album.getId(), coverMultipart);
                log.info("Attached cover image to album '{}'", albumTitle);
            } catch (Exception e) {
                log.warn("Failed to set cover for album '{}': {}", albumTitle, e.getMessage());
            }
        }

        List<String> failedTracks = new ArrayList<>();
        int importedCount = 0;
        int trackNum = 1;

        for (int i = 0; i < extractedAudioFiles.size(); i++) {
            Path audioPath = extractedAudioFiles.get(i);
            String rawFileName = audioPath.getFileName().toString();
            log.info("Adding track [{}/{}]: {}", i + 1, extractedAudioFiles.size(), rawFileName);

            try {
                AudioTagService.TagInfo info = tagService.readTags(audioPath.toFile());
                String trackTitle = info.title() != null ? info.title() : cleanFilename(rawFileName);
                int num = info.trackNumber() != null ? info.trackNumber() : trackNum;

                byte[] bytes = Files.readAllBytes(audioPath);
                String mimeType = determineAudioMimeType(rawFileName);
                BervanMockMultiPartFile multipart = new BervanMockMultiPartFile(
                        rawFileName,
                        rawFileName,
                        mimeType,
                        bytes
                );

                storageService.addTrack(album.getId(), multipart, trackTitle, artist, num);
                importedCount++;
                trackNum++;
                log.info("Added track [{}/{}]: '{}' (track #{})", i + 1, extractedAudioFiles.size(), trackTitle, num);
            } catch (Exception e) {
                log.error("Failed to add track '{}' to album '{}': {}", rawFileName, album.getTitle(), e.getMessage(), e);
                failedTracks.add(rawFileName + " (" + e.getMessage() + ")");
            }
        }

        AudioAlbum refreshed = storageService.getAlbumById(album.getId()).orElse(album);
        String summaryMsg = String.format("Successfully imported album '%s' with %d tracks", refreshed.getTitle(), importedCount);
        if (!failedTracks.isEmpty()) {
            summaryMsg += String.format(" (%d track(s) failed)", failedTracks.size());
        }
        log.info("Single-album ZIP import complete: {}", summaryMsg);

        return AudioZipImportResult.builder()
                .importedTracksCount(importedCount)
                .albumsCount(1)
                .affectedAlbumTitles(List.of(refreshed.getArtist() + " - " + refreshed.getTitle()))
                .failedTracks(failedTracks)
                .primaryAlbum(refreshed)
                .message(summaryMsg)
                .build();
    }

    private String determineAudioMimeType(String filename) {
        if (filename == null) return "audio/mpeg";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "audio/mpeg";
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
