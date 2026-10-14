package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Year;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AudioStorageService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");
    private final AudioTagService audioTagService;
    private final AudioAiMetadataService aiMetadataService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${file.service.storage.folder.main:}")
    private String mainStorageFolder;

    @Value("${file.service.storage.folder[main]:}")
    private String mainStorageFolderBracket;

    @Value("${file.service.storage.folders.mapping:}")
    private String folderMappingStr;

    @Value("${audio-streaming.file-storage-relative-path:/Audio Library}")
    private String audioRelativePath;

    private final Map<String, AudioAlbum> albumsById = new ConcurrentHashMap<>();
    private final Map<String, AudioTrack> tracksById = new ConcurrentHashMap<>();

    public AudioStorageService(AudioTagService audioTagService, AudioAiMetadataService aiMetadataService) {
        this.audioTagService = audioTagService;
        this.aiMetadataService = aiMetadataService;
    }

    @PostConstruct
    public void init() {
        try {
            Path root = getAudioLibraryRoot();
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Created audio library directory at: {}", root.toAbsolutePath());
            }
            reloadAll();
        } catch (Exception e) {
            log.error("Failed to initialize AudioStorageService: {}", e.getMessage(), e);
        }
    }

    public Path getAudioLibraryRoot() {
        String base = null;
        if (mainStorageFolder != null && !mainStorageFolder.isBlank()) {
            base = mainStorageFolder.trim();
        } else if (mainStorageFolderBracket != null && !mainStorageFolderBracket.isBlank()) {
            base = mainStorageFolderBracket.trim();
        } else if (folderMappingStr != null && !folderMappingStr.isBlank()) {
            for (String mapping : folderMappingStr.split(",")) {
                String[] parts = mapping.split(":", 2);
                if (parts.length == 2) {
                    if ("audio".equalsIgnoreCase(parts[0].trim()) || "music".equalsIgnoreCase(parts[0].trim())) {
                        return Paths.get(parts[1].trim()).toAbsolutePath().normalize();
                    } else if ("main".equalsIgnoreCase(parts[0].trim())) {
                        base = parts[1].trim();
                    }
                }
            }
        }

        if (base == null || base.isBlank()) {
            base = System.getProperty("user.dir") + File.separator + "files";
        }

        String rel = (audioRelativePath != null && !audioRelativePath.isBlank())
                ? audioRelativePath.trim()
                : "/Audio Library";

        if (rel.startsWith("/") || rel.startsWith("\\")) {
            rel = rel.substring(1);
        }

        return Paths.get(base, rel).toAbsolutePath().normalize();
    }

    public synchronized void reloadAll() {
        log.info("Reloading audio library from disk...");
        albumsById.clear();
        tracksById.clear();

        Path root = getAudioLibraryRoot();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.warn("Audio library root does not exist: {}", root);
            return;
        }

        try (var artistDirs = Files.list(root)) {
            artistDirs.filter(Files::isDirectory).forEach(artistPath -> {
                String artistDirName = artistPath.getFileName().toString();
                if (artistDirName.startsWith(".")) return;

                try (var albumDirs = Files.list(artistPath)) {
                    albumDirs.filter(Files::isDirectory).forEach(albumPath -> {
                        String albumDirName = albumPath.getFileName().toString();
                        if (albumDirName.startsWith(".")) return;

                        try {
                            AudioAlbum album = loadOrScanAlbum(albumPath, artistDirName);
                            if (album != null) {
                                albumsById.put(album.getId(), album);
                                for (AudioTrack track : album.getTracks()) {
                                    tracksById.put(track.getId(), track);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error loading album from {}: {}", albumPath, e.getMessage(), e);
                        }
                    });
                } catch (Exception e) {
                    log.error("Error reading albums in artist folder {}: {}", artistPath, e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("Error reading audio library root: {}", e.getMessage(), e);
        }

        log.info("Loaded {} albums and {} tracks into memory.", albumsById.size(), tracksById.size());
    }

    private AudioAlbum loadOrScanAlbum(Path albumPath, String artistDirName) throws IOException {
        Path albumJsonPath = albumPath.resolve("album.json");
        AudioAlbum album;

        if (Files.exists(albumJsonPath)) {
            try {
                album = objectMapper.readValue(Files.readString(albumJsonPath, StandardCharsets.UTF_8), AudioAlbum.class);
                validateAndRefreshAlbumTracks(album, albumPath, artistDirName);
            } catch (Exception e) {
                log.warn("Could not read album.json in {}, rescanning from audio files: {}", albumPath, e.getMessage());
                album = scanAlbumFromFiles(albumPath, artistDirName);
            }
        } else {
            album = scanAlbumFromFiles(albumPath, artistDirName);
        }

        if (album != null) {
            // Check for cover image
            Path coverPath = findCoverImage(albumPath);
            if (coverPath != null) {
                album.setCoverFilename(coverPath.getFileName().toString());
                album.setHasCover(true);
            } else {
                album.setHasCover(false);
            }

            // Always ensure playlist.m3u8 is up to date
            saveAlbumJson(album, albumPath);
            generateM3u8Playlist(album, albumPath);
        }

        return album;
    }

    private void validateAndRefreshAlbumTracks(AudioAlbum album, Path albumPath, String artistDirName) {
        // Check if files listed in album.json still exist on disk and check for new audio files
        Map<String, Path> audioFilesOnDisk = findAudioFiles(albumPath);
        List<AudioTrack> updatedTracks = new ArrayList<>();

        for (AudioTrack track : album.getTracks()) {
            if (audioFilesOnDisk.containsKey(track.getFilename())) {
                Path trackFile = audioFilesOnDisk.remove(track.getFilename());
                checkTagMismatch(track, trackFile.toFile(), artistDirName, album.getTitle());
                updatedTracks.add(track);
            }
        }

        // Add any newly discovered audio files not yet in album.json
        for (Path newFile : audioFilesOnDisk.values()) {
            AudioTrack newTrack = createTrackFromFile(newFile.toFile(), albumPath, artistDirName, album.getTitle(), updatedTracks.size() + 1);
            updatedTracks.add(newTrack);
        }

        updatedTracks.sort(Comparator.comparing(
                t -> t.getTrackNumber() != null ? t.getTrackNumber() : 999
        ));

        Set<String> albumGenres = new LinkedHashSet<>(album.getGenres() != null ? album.getGenres() : List.of());
        for (AudioTrack track : updatedTracks) {
            if (track.getGenres() == null || track.getGenres().isEmpty()) {
                if (track.getGenre() != null && !track.getGenre().isBlank()) {
                    track.setGenres(AudioTagService.parseGenres(track.getGenre()));
                } else if (!albumGenres.isEmpty()) {
                    track.setGenres(new ArrayList<>(albumGenres));
                    track.setGenre(AudioTagService.formatGenresForTag(new ArrayList<>(albumGenres)));
                }
            }
            if (track.getGenres() != null) {
                albumGenres.addAll(track.getGenres());
            }
        }
        if (!albumGenres.isEmpty()) {
            album.setGenres(new ArrayList<>(albumGenres));
        }

        album.setTracks(updatedTracks);
        album.setTrackCount(updatedTracks.size());
        album.setTotalDurationSeconds(updatedTracks.stream().mapToDouble(t -> t.getDurationSeconds() != null ? t.getDurationSeconds() : 0.0).sum());

        boolean anyMismatch = updatedTracks.stream().anyMatch(AudioTrack::isHasTagMismatch);
        album.setHasTagMismatch(anyMismatch);
    }

    private AudioAlbum scanAlbumFromFiles(Path albumPath, String artistDirName) {
        String albumDirName = albumPath.getFileName().toString();
        boolean isSingle = albumDirName.equalsIgnoreCase("Singles") || albumDirName.toLowerCase().startsWith("single");

        Integer year = parseYearFromDir(albumDirName);
        String albumTitle = cleanAlbumTitle(albumDirName);

        Map<String, Path> audioFiles = findAudioFiles(albumPath);
        if (audioFiles.isEmpty() && !isSingle) {
            return null; // Empty non-singles folder
        }

        List<AudioTrack> tracks = new ArrayList<>();
        int trackIndex = 1;
        Set<String> albumGenres = new LinkedHashSet<>();
        byte[] extractedCover = null;

        for (Path file : audioFiles.values()) {
            AudioTrack track = createTrackFromFile(file.toFile(), albumPath, artistDirName, albumTitle, trackIndex++);
            tracks.add(track);
            if (track.getGenres() != null && !track.getGenres().isEmpty()) {
                albumGenres.addAll(track.getGenres());
            } else if (track.getGenre() != null && !track.getGenre().isBlank()) {
                albumGenres.addAll(AudioTagService.parseGenres(track.getGenre()));
            }
            if (extractedCover == null) {
                AudioTagService.TagInfo tagInfo = audioTagService.readTags(file.toFile());
                if (tagInfo.albumImage() != null && tagInfo.albumImage().length > 0) {
                    extractedCover = tagInfo.albumImage();
                }
            }
        }

        tracks.sort(Comparator.comparing(t -> t.getTrackNumber() != null ? t.getTrackNumber() : 999));

        // If cover file does not exist, but we found an embedded image, save it as cover.jpg
        Path existingCover = findCoverImage(albumPath);
        if (existingCover == null && extractedCover != null) {
            try {
                Path coverDest = albumPath.resolve("cover.jpg");
                Files.write(coverDest, extractedCover);
                existingCover = coverDest;
                log.info("Extracted embedded cover art to {}", coverDest);
            } catch (Exception e) {
                log.warn("Failed to write extracted cover: {}", e.getMessage());
            }
        }

        String albumId = UUID.nameUUIDFromBytes((artistDirName + "/" + albumDirName).getBytes(StandardCharsets.UTF_8)).toString();

        return AudioAlbum.builder()
                .id(albumId)
                .title(albumTitle)
                .artist(artistDirName)
                .artists(List.of(artistDirName))
                .releaseYear(year != null ? year : (tracks.isEmpty() ? Year.now().getValue() : tracks.get(0).getYear()))
                .genres(albumGenres.isEmpty() ? List.of("Unknown") : new ArrayList<>(albumGenres))
                .description("")
                .folderPath(getAudioLibraryRoot().relativize(albumPath).toString())
                .tracks(tracks)
                .trackCount(tracks.size())
                .totalDurationSeconds(tracks.stream().mapToDouble(t -> t.getDurationSeconds() != null ? t.getDurationSeconds() : 0.0).sum())
                .isSingle(isSingle)
                .hasCover(existingCover != null)
                .coverFilename(existingCover != null ? existingCover.getFileName().toString() : null)
                .hasTagMismatch(tracks.stream().anyMatch(AudioTrack::isHasTagMismatch))
                .build();
    }

    private AudioTrack createTrackFromFile(File file, Path albumPath, String defaultArtist, String defaultAlbum, int defaultTrackNum) {
        AudioTagService.TagInfo tags = audioTagService.readTags(file);

        String title = tags.title() != null ? tags.title() : cleanFilename(file.getName());
        String artist = tags.artist() != null ? tags.artist() : defaultArtist;
        String albumName = tags.album() != null ? tags.album() : defaultAlbum;
        Integer trackNum = tags.trackNumber() != null ? tags.trackNumber() : defaultTrackNum;
        Integer year = tags.year();

        List<String> trackGenres = AudioTagService.parseGenres(tags.genre());
        if (trackGenres.isEmpty()) {
            trackGenres = new ArrayList<>(List.of("Unknown"));
        }
        String genre = AudioTagService.formatGenresForTag(trackGenres);
        if (genre == null) genre = "Unknown";

        String relativePath = getAudioLibraryRoot().relativize(file.toPath()).toString();
        String trackId = UUID.nameUUIDFromBytes(relativePath.getBytes(StandardCharsets.UTF_8)).toString();

        AudioTrack track = AudioTrack.builder()
                .id(trackId)
                .title(title)
                .artist(artist)
                .artists(List.of(artist))
                .album(albumName)
                .albumArtist(defaultArtist)
                .trackNumber(trackNum)
                .discNumber(1)
                .durationSeconds(tags.durationSeconds() != null ? tags.durationSeconds() : 0.0)
                .year(year)
                .genre(genre)
                .genres(trackGenres)
                .filename(file.getName())
                .relativePath(relativePath)
                .fileSize(file.length())
                .format(getFileExtension(file.getName()))
                .bitrate(tags.bitrate())
                .build();

        checkTagMismatch(track, file, defaultArtist, defaultAlbum);
        return track;
    }

    private void checkTagMismatch(AudioTrack track, File file, String expectedArtist, String expectedAlbum) {
        AudioTagService.TagInfo tags = audioTagService.readTags(file);
        if (tags.artist() != null && !isFuzzyMatch(tags.artist(), expectedArtist)) {
            track.setHasTagMismatch(true);
            track.setTagMismatchDetails("MP3 tag artist is '" + tags.artist() + "', but directory is '" + expectedArtist + "'");
            log.warn("Tag mismatch for file [{}]: tag artist is [{}], folder is [{}]", file.getName(), tags.artist(), expectedArtist);
        } else if (tags.album() != null && !isFuzzyMatch(tags.album(), expectedAlbum)) {
            track.setHasTagMismatch(true);
            track.setTagMismatchDetails("MP3 tag album is '" + tags.album() + "', but directory is '" + expectedAlbum + "'");
            log.warn("Tag mismatch for file [{}]: tag album is [{}], folder is [{}]", file.getName(), tags.album(), expectedAlbum);
        } else {
            track.setHasTagMismatch(false);
            track.setTagMismatchDetails(null);
        }
    }

    private boolean isFuzzyMatch(String a, String b) {
        if (a == null || b == null) return false;
        String na = a.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String nb = b.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    public synchronized void saveAlbumJson(AudioAlbum album, Path albumPath) {
        try {
            Path target = albumPath.resolve("album.json");
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(album);
            Files.writeString(target, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to write album.json in {}: {}", albumPath, e.getMessage(), e);
        }
    }

    public synchronized void generateM3u8Playlist(AudioAlbum album, Path albumPath) {
        try {
            Path m3u8Path = albumPath.resolve("playlist.m3u8");
            StringBuilder sb = new StringBuilder();
            sb.append("#EXTM3U\n");

            for (AudioTrack track : album.getTracks()) {
                int duration = track.getDurationSeconds() != null ? track.getDurationSeconds().intValue() : -1;
                String artist = track.getArtist() != null ? track.getArtist() : album.getArtist();
                sb.append(String.format("#EXTINF:%d,%s - %s\n", duration, artist, track.getTitle()));
                sb.append(track.getFilename()).append("\n");
            }

            Files.writeString(m3u8Path, sb.toString(), StandardCharsets.UTF_8);
            log.debug("Generated playlist.m3u8 for album: {}", album.getTitle());
        } catch (Exception e) {
            log.error("Failed to generate playlist.m3u8 in {}: {}", albumPath, e.getMessage(), e);
        }
    }

    public synchronized AudioAlbum createAlbum(CreateAlbumRequest request, MultipartFile cover) throws IOException {
        String artist = (request.getArtist() != null && !request.getArtist().isBlank())
                ? request.getArtist().trim() : "Unknown Artist";
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim() : "Unknown Album";
        Integer year = request.getReleaseYear() != null ? request.getReleaseYear() : Year.now().getValue();

        String folderName = request.isSingle() ? "Singles" : sanitizeFileName(title + " (" + year + ")");
        Path artistDir = getAudioLibraryRoot().resolve(sanitizeFileName(artist));
        Path albumDir = artistDir.resolve(folderName);

        Files.createDirectories(albumDir);

        String albumId = UUID.nameUUIDFromBytes((artist + "/" + folderName).getBytes(StandardCharsets.UTF_8)).toString();

        String coverFilename = null;
        boolean hasCover = false;
        if (cover != null && !cover.isEmpty()) {
            coverFilename = "cover.jpg";
            Path coverPath = albumDir.resolve(coverFilename);
            Files.copy(cover.getInputStream(), coverPath, StandardCopyOption.REPLACE_EXISTING);
            hasCover = true;
        }

        AudioAlbum album = AudioAlbum.builder()
                .id(albumId)
                .title(title)
                .artist(artist)
                .artists(request.getArtists() != null && !request.getArtists().isEmpty() ? request.getArtists() : List.of(artist))
                .releaseYear(year)
                .genres(request.getGenres() != null && !request.getGenres().isEmpty() ? request.getGenres() : List.of("Unknown"))
                .description(request.getDescription() != null ? request.getDescription() : "")
                .coverFilename(coverFilename)
                .hasCover(hasCover)
                .folderPath(getAudioLibraryRoot().relativize(albumDir).toString())
                .tracks(new ArrayList<>())
                .trackCount(0)
                .totalDurationSeconds(0.0)
                .isSingle(request.isSingle())
                .hasTagMismatch(false)
                .build();

        saveAlbumJson(album, albumDir);
        generateM3u8Playlist(album, albumDir);

        albumsById.put(album.getId(), album);
        log.info("Created new album [{}] at {}", title, albumDir);
        return album;
    }

    public synchronized AudioTrack addTrack(String albumId, MultipartFile file, String overrideTitle,
                                           String overrideArtist, Integer overrideTrackNum) throws Exception {
        return addTrack(albumId, file, overrideTitle, overrideArtist, overrideTrackNum, (List<String>) null);
    }

    public synchronized AudioTrack addTrack(String albumId, MultipartFile file, String overrideTitle,
                                           String overrideArtist, Integer overrideTrackNum, String overrideGenres) throws Exception {
        List<String> parsed = overrideGenres != null ? AudioTagService.parseGenres(overrideGenres) : null;
        return addTrack(albumId, file, overrideTitle, overrideArtist, overrideTrackNum, parsed);
    }

    public synchronized AudioTrack addTrack(String albumId, MultipartFile file, String overrideTitle,
                                           String overrideArtist, Integer overrideTrackNum, List<String> overrideGenres) throws Exception {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null) {
            throw new IllegalArgumentException("Album not found with ID: " + albumId);
        }

        Path albumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
        Files.createDirectories(albumDir);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "track.mp3";
        }

        int trackNum = overrideTrackNum != null ? overrideTrackNum : album.getTracks().size() + 1;
        String ext = getFileExtension(originalFilename);
        String cleanTitle = (overrideTitle != null && !overrideTitle.isBlank()) ? overrideTitle.trim() : cleanFilename(originalFilename);

        String sanitizedFilename = String.format("%02d - %s.%s", trackNum, sanitizeFileName(cleanTitle), ext);
        Path targetPath = albumDir.resolve(sanitizedFilename);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String trackArtist = (overrideArtist != null && !overrideArtist.isBlank()) ? overrideArtist.trim() : album.getArtist();
        List<String> trackGenres = (overrideGenres != null && !overrideGenres.isEmpty()) ? overrideGenres : album.getGenres();
        String genreStr = AudioTagService.formatGenresForTag(trackGenres);
        if (genreStr == null) genreStr = "Unknown";

        // Always write canonical ID3 tags to the MP3 file on disk
        if ("mp3".equalsIgnoreCase(ext)) {
            audioTagService.updateTags(targetPath.toFile(), cleanTitle, trackArtist, album.getTitle(), trackNum, album.getReleaseYear(), genreStr);
        }

        // If album has no cover yet, check if file had an embedded cover image
        if (!album.isHasCover()) {
            AudioTagService.TagInfo tagInfo = audioTagService.readTags(targetPath.toFile());
            if (tagInfo.albumImage() != null && tagInfo.albumImage().length > 0) {
                Path coverDest = albumDir.resolve("cover.jpg");
                Files.write(coverDest, tagInfo.albumImage());
                album.setCoverFilename("cover.jpg");
                album.setHasCover(true);
            }
        }

        AudioTrack newTrack = createTrackFromFile(targetPath.toFile(), albumDir, album.getArtist(), album.getTitle(), trackNum);
        if (overrideGenres != null && !overrideGenres.isEmpty()) {
            newTrack.setGenres(new ArrayList<>(overrideGenres));
            newTrack.setGenre(genreStr);
        }
        album.getTracks().add(newTrack);
        album.getTracks().sort(Comparator.comparing(t -> t.getTrackNumber() != null ? t.getTrackNumber() : 999));
        album.setTrackCount(album.getTracks().size());
        album.setTotalDurationSeconds(album.getTracks().stream().mapToDouble(t -> t.getDurationSeconds() != null ? t.getDurationSeconds() : 0.0).sum());

        // Keep album genres in sync
        Set<String> allAlbumGenres = new LinkedHashSet<>(album.getGenres() != null ? album.getGenres() : List.of());
        if (newTrack.getGenres() != null) {
            allAlbumGenres.addAll(newTrack.getGenres());
        }
        album.setGenres(new ArrayList<>(allAlbumGenres));

        saveAlbumJson(album, albumDir);
        generateM3u8Playlist(album, albumDir);

        tracksById.put(newTrack.getId(), newTrack);
        log.info("Added track [{}] to album [{}]", cleanTitle, album.getTitle());
        return newTrack;
    }

    public synchronized AudioAlbum updateAlbum(String albumId, UpdateAlbumRequest request) throws IOException {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null) throw new IllegalArgumentException("Album not found: " + albumId);

        Path oldAlbumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
        String oldArtist = album.getArtist();
        String oldTitle = album.getTitle();

        String newArtist = (request.getArtist() != null && !request.getArtist().isBlank()) ? request.getArtist().trim() : oldArtist;
        String newTitle = (request.getTitle() != null && !request.getTitle().isBlank()) ? request.getTitle().trim() : oldTitle;
        Integer newYear = request.getReleaseYear() != null ? request.getReleaseYear() : album.getReleaseYear();
        List<String> newGenres = request.getGenres() != null ? request.getGenres() : album.getGenres();
        String genreTagStr = AudioTagService.formatGenresForTag(newGenres);
        if (genreTagStr == null) genreTagStr = "Unknown";

        boolean artistChanged = !oldArtist.equalsIgnoreCase(newArtist);
        boolean titleOrYearChanged = !oldTitle.equalsIgnoreCase(newTitle) || !Objects.equals(album.getReleaseYear(), newYear);

        Path newAlbumDir = oldAlbumDir;

        // Cascade rename / move folder on disk if artist or title changed
        if (artistChanged || titleOrYearChanged) {
            String folderName = album.isSingle() ? "Singles" : sanitizeFileName(newTitle + " (" + newYear + ")");
            Path newArtistDir = getAudioLibraryRoot().resolve(sanitizeFileName(newArtist));
            Files.createDirectories(newArtistDir);
            newAlbumDir = newArtistDir.resolve(folderName);

            if (!oldAlbumDir.equals(newAlbumDir)) {
                Files.move(oldAlbumDir, newAlbumDir, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved album directory from {} to {}", oldAlbumDir, newAlbumDir);
                album.setFolderPath(getAudioLibraryRoot().relativize(newAlbumDir).toString());

                // Clean up old artist directory if empty
                Path oldArtistDir = oldAlbumDir.getParent();
                if (oldArtistDir != null && Files.exists(oldArtistDir)) {
                    try (var stream = Files.list(oldArtistDir)) {
                        if (stream.findAny().isEmpty()) {
                            Files.deleteIfExists(oldArtistDir);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Cascade update ID3 tags across ALL tracks in this album
        for (AudioTrack track : album.getTracks()) {
            Path trackFile = newAlbumDir.resolve(track.getFilename());
            if (Files.exists(trackFile) && trackFile.toString().toLowerCase().endsWith(".mp3")) {
                audioTagService.updateTags(
                        trackFile.toFile(),
                        track.getTitle(),
                        artistChanged ? newArtist : track.getArtist(),
                        newTitle,
                        track.getTrackNumber(),
                        newYear,
                        genreTagStr
                );
            }
            track.setAlbum(newTitle);
            track.setYear(newYear);
            track.setGenre(genreTagStr);
            track.setGenres(newGenres != null ? new ArrayList<>(newGenres) : new ArrayList<>());
            if (artistChanged) {
                track.setArtist(newArtist);
                track.setArtists(List.of(newArtist));
            }
            track.setRelativePath(getAudioLibraryRoot().relativize(trackFile).toString());
            track.setHasTagMismatch(false);
            track.setTagMismatchDetails(null);
            tracksById.put(track.getId(), track);
        }

        album.setTitle(newTitle);
        album.setArtist(newArtist);
        album.setArtists(request.getArtists() != null && !request.getArtists().isEmpty() ? request.getArtists() : List.of(newArtist));
        album.setReleaseYear(newYear);
        album.setGenres(newGenres);
        if (request.getDescription() != null) album.setDescription(request.getDescription());
        album.setHasTagMismatch(false);

        saveAlbumJson(album, newAlbumDir);
        generateM3u8Playlist(album, newAlbumDir);

        albumsById.put(album.getId(), album);
        log.info("Updated album metadata and cascaded ID3 tags to all tracks for album [{}]", newTitle);
        return album;
    }

    public synchronized AudioTrack updateTrack(String albumId, String trackId, UpdateTrackRequest request) {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null) throw new IllegalArgumentException("Album not found: " + albumId);

        AudioTrack track = album.getTracks().stream()
                .filter(t -> t.getId().equals(trackId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));

        Path albumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
        Path trackFile = albumDir.resolve(track.getFilename());

        if (request.getTitle() != null && !request.getTitle().isBlank()) track.setTitle(request.getTitle().trim());
        if (request.getArtist() != null && !request.getArtist().isBlank()) {
            track.setArtist(request.getArtist().trim());
            track.setArtists(request.getArtists() != null ? request.getArtists() : List.of(track.getArtist()));
        }
        if (request.getTrackNumber() != null) track.setTrackNumber(request.getTrackNumber());
        if (request.getYear() != null) track.setYear(request.getYear());

        if (request.getGenres() != null && !request.getGenres().isEmpty()) {
            track.setGenres(new ArrayList<>(request.getGenres()));
            track.setGenre(AudioTagService.formatGenresForTag(request.getGenres()));
        } else if (request.getGenre() != null && !request.getGenre().isBlank()) {
            List<String> parsed = AudioTagService.parseGenres(request.getGenre());
            track.setGenres(parsed);
            track.setGenre(AudioTagService.formatGenresForTag(parsed));
        }

        // Aggregate newly added track genres to album.getGenres()
        Set<String> allAlbumGenres = new LinkedHashSet<>(album.getGenres() != null ? album.getGenres() : List.of());
        if (track.getGenres() != null) {
            allAlbumGenres.addAll(track.getGenres());
        }
        album.setGenres(new ArrayList<>(allAlbumGenres));

        // Update ID3 tags in physical file
        if (Files.exists(trackFile) && trackFile.toString().toLowerCase().endsWith(".mp3")) {
            audioTagService.updateTags(
                    trackFile.toFile(),
                    track.getTitle(),
                    track.getArtist(),
                    album.getTitle(),
                    track.getTrackNumber(),
                    track.getYear() != null ? track.getYear() : album.getReleaseYear(),
                    track.getGenre()
            );
        }

        track.setHasTagMismatch(false);
        track.setTagMismatchDetails(null);

        album.getTracks().sort(Comparator.comparing(t -> t.getTrackNumber() != null ? t.getTrackNumber() : 999));
        boolean anyMismatch = album.getTracks().stream().anyMatch(AudioTrack::isHasTagMismatch);
        album.setHasTagMismatch(anyMismatch);

        saveAlbumJson(album, albumDir);
        generateM3u8Playlist(album, albumDir);

        tracksById.put(track.getId(), track);
        log.info("Updated track [{}] and saved ID3 tags", track.getTitle());
        return track;
    }

    public synchronized void deleteTrack(String albumId, String trackId) throws IOException {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null) throw new IllegalArgumentException("Album not found: " + albumId);

        AudioTrack track = album.getTracks().stream()
                .filter(t -> t.getId().equals(trackId))
                .findFirst()
                .orElse(null);

        if (track != null) {
            Path albumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
            Path trackFile = albumDir.resolve(track.getFilename());
            Files.deleteIfExists(trackFile);

            album.getTracks().remove(track);
            album.setTrackCount(album.getTracks().size());
            album.setTotalDurationSeconds(album.getTracks().stream().mapToDouble(t -> t.getDurationSeconds() != null ? t.getDurationSeconds() : 0.0).sum());

            saveAlbumJson(album, albumDir);
            generateM3u8Playlist(album, albumDir);

            tracksById.remove(trackId);
            log.info("Deleted track [{}] from album [{}]", track.getTitle(), album.getTitle());
        }
    }

    public synchronized void deleteAlbum(String albumId) throws IOException {
        AudioAlbum album = albumsById.remove(albumId);
        if (album != null) {
            for (AudioTrack track : album.getTracks()) {
                tracksById.remove(track.getId());
            }

            Path albumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
            if (Files.exists(albumDir)) {
                deleteDirectoryRecursively(albumDir);
                log.info("Deleted album folder: {}", albumDir);

                // Delete parent artist dir if empty
                Path artistDir = albumDir.getParent();
                if (artistDir != null && Files.exists(artistDir)) {
                    try (var stream = Files.list(artistDir)) {
                        if (stream.findAny().isEmpty()) {
                            Files.deleteIfExists(artistDir);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public synchronized void synchronizeTags(String albumId) {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null) throw new IllegalArgumentException("Album not found: " + albumId);

        Path albumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
        String genre = (album.getGenres() != null && !album.getGenres().isEmpty()) ? album.getGenres().get(0) : "Unknown";

        for (AudioTrack track : album.getTracks()) {
            Path trackFile = albumDir.resolve(track.getFilename());
            if (Files.exists(trackFile) && trackFile.toString().toLowerCase().endsWith(".mp3")) {
                audioTagService.updateTags(
                        trackFile.toFile(),
                        track.getTitle(),
                        album.getArtist(),
                        album.getTitle(),
                        track.getTrackNumber(),
                        album.getReleaseYear(),
                        genre
                );
            }
            track.setArtist(album.getArtist());
            track.setAlbum(album.getTitle());
            track.setGenre(genre);
            track.setHasTagMismatch(false);
            track.setTagMismatchDetails(null);
            tracksById.put(track.getId(), track);
        }

        album.setHasTagMismatch(false);
        saveAlbumJson(album, albumDir);
        log.info("Synchronized ID3 tags for all tracks in album [{}]", album.getTitle());
    }

    public synchronized void updateCover(String albumId, MultipartFile coverFile) throws IOException {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null) throw new IllegalArgumentException("Album not found: " + albumId);

        Path albumDir = getAudioLibraryRoot().resolve(album.getFolderPath());
        String filename = "cover.jpg";
        Path dest = albumDir.resolve(filename);
        Files.copy(coverFile.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        album.setCoverFilename(filename);
        album.setHasCover(true);
        saveAlbumJson(album, albumDir);
        log.info("Updated cover for album [{}]", album.getTitle());
    }

    public File getTrackFile(String trackId) {
        AudioTrack track = tracksById.get(trackId);
        if (track == null) return null;
        Path path = getAudioLibraryRoot().resolve(track.getRelativePath());
        File file = path.toFile();
        return file.exists() ? file : null;
    }

    public File getCoverFile(String albumId) {
        AudioAlbum album = albumsById.get(albumId);
        if (album == null || !album.isHasCover() || album.getCoverFilename() == null) return null;
        Path path = getAudioLibraryRoot().resolve(album.getFolderPath()).resolve(album.getCoverFilename());
        File file = path.toFile();
        return file.exists() ? file : null;
    }

    public List<AudioAlbum> getAllAlbums() {
        return new ArrayList<>(albumsById.values());
    }

    public Optional<AudioAlbum> getAlbumById(String id) {
        return Optional.ofNullable(albumsById.get(id));
    }

    public Optional<AudioTrack> getTrackById(String id) {
        return Optional.ofNullable(tracksById.get(id));
    }

    public AudioLibrarySummary getLibrarySummary(String query, String genre, String artist, Integer year) {
        List<AudioAlbum> filtered = albumsById.values().stream()
                .filter(a -> {
                    if (query != null && !query.isBlank()) {
                        String q = query.trim().toLowerCase();
                        boolean matchesAlbum = a.getTitle().toLowerCase().contains(q) || a.getArtist().toLowerCase().contains(q);
                        boolean matchesTrack = a.getTracks().stream().anyMatch(t -> t.getTitle().toLowerCase().contains(q) || t.getArtist().toLowerCase().contains(q));
                        if (!matchesAlbum && !matchesTrack) return false;
                    }
                    if (genre != null && !genre.isBlank()) {
                        String gLower = genre.trim().toLowerCase();
                        boolean albumMatches = a.getGenres() != null && a.getGenres().stream().anyMatch(g -> g.equalsIgnoreCase(genre.trim()));
                        boolean trackMatches = a.getTracks().stream().anyMatch(t ->
                                (t.getGenres() != null && t.getGenres().stream().anyMatch(tg -> tg.equalsIgnoreCase(genre.trim()))) ||
                                (t.getGenre() != null && t.getGenre().toLowerCase().contains(gLower))
                        );
                        if (!albumMatches && !trackMatches) {
                            return false;
                        }
                    }
                    if (artist != null && !artist.isBlank()) {
                        if (!a.getArtist().equalsIgnoreCase(artist.trim()) &&
                                (a.getArtists() == null || a.getArtists().stream().noneMatch(ar -> ar.equalsIgnoreCase(artist.trim())))) {
                            return false;
                        }
                    }
                    if (year != null) {
                        if (!Objects.equals(a.getReleaseYear(), year)) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(AudioAlbum::getTitle, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        Set<String> distinctGenres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (AudioAlbum a : albumsById.values()) {
            if (a.getGenres() != null) {
                distinctGenres.addAll(a.getGenres());
            }
            for (AudioTrack t : a.getTracks()) {
                if (t.getGenres() != null) {
                    distinctGenres.addAll(t.getGenres());
                }
            }
        }
        distinctGenres.removeIf(String::isBlank);

        Set<String> distinctArtists = albumsById.values().stream()
                .map(AudioAlbum::getArtist)
                .filter(ar -> ar != null && !ar.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

        int totalTracks = albumsById.values().stream().mapToInt(AudioAlbum::getTrackCount).sum();
        double totalDuration = albumsById.values().stream()
                .mapToDouble(a -> a.getTotalDurationSeconds() != null ? a.getTotalDurationSeconds() : 0.0)
                .sum();

        return AudioLibrarySummary.builder()
                .totalAlbums(albumsById.size())
                .totalTracks(totalTracks)
                .totalArtists(distinctArtists.size())
                .totalDurationSeconds(totalDuration)
                .albums(filtered)
                .genres(new ArrayList<>(distinctGenres))
                .artists(new ArrayList<>(distinctArtists))
                .build();
    }

    private Map<String, Path> findAudioFiles(Path dir) {
        Map<String, Path> result = new TreeMap<>();
        if (!Files.exists(dir)) return result;
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String fn = p.getFileName().toString().toLowerCase();
                if (fn.endsWith(".mp3") || fn.endsWith(".flac") || fn.endsWith(".wav") || fn.endsWith(".m4a") || fn.endsWith(".ogg")) {
                    result.put(p.getFileName().toString(), p);
                }
            });
        } catch (Exception ignored) {}
        return result;
    }

    private Path findCoverImage(Path dir) {
        if (!Files.exists(dir)) return null;
        List<String> names = List.of("cover.jpg", "folder.jpg", "cover.png", "folder.png", "front.jpg", "front.png");
        for (String name : names) {
            Path p = dir.resolve(name);
            if (Files.exists(p) && Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private Integer parseYearFromDir(String dirName) {
        var matcher = java.util.regex.Pattern.compile("\\((\\d{4})\\)").matcher(dirName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String cleanAlbumTitle(String dirName) {
        return dirName.replaceAll("\\(\\d{4}\\)", "").trim();
    }

    private String cleanFilename(String fn) {
        int lastDot = fn.lastIndexOf('.');
        String name = lastDot > 0 ? fn.substring(0, lastDot) : fn;
        return name.replaceFirst("^\\d+\\s*[-._]\\s*", "").trim();
    }

    private String getFileExtension(String fn) {
        int idx = fn.lastIndexOf('.');
        return idx > 0 ? fn.substring(idx + 1).toLowerCase() : "";
    }

    private String sanitizeFileName(String input) {
        if (input == null) return "Unknown";
        return input.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
