package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.logging.JsonLogger;
import com.mpatric.mp3agic.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AudioTagService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");

    public record TagInfo(
            String title,
            String artist,
            String album,
            Integer trackNumber,
            Integer year,
            String genre,
            Double durationSeconds,
            Integer bitrate,
            byte[] albumImage,
            String albumImageMimeType
    ) {}

    public TagInfo readTags(File file) {
        if (file == null || !file.exists()) {
            return new TagInfo(null, null, null, null, null, null, null, null, null, null);
        }

        String filename = file.getName().toLowerCase();
        if (!filename.endsWith(".mp3")) {
            return new TagInfo(null, null, null, null, null, null, null, null, null, null);
        }

        try {
            Mp3File mp3file = new Mp3File(file);
            Double duration = (double) mp3file.getLengthInSeconds();
            Integer bitrate = mp3file.getBitrate();

            String title = null;
            String artist = null;
            String album = null;
            Integer track = null;
            Integer year = null;
            String genre = null;
            byte[] albumImage = null;
            String albumImageMimeType = null;

            if (mp3file.hasId3v2Tag()) {
                ID3v2 id3v2 = mp3file.getId3v2Tag();
                title = trimOrNull(id3v2.getTitle());
                artist = trimOrNull(id3v2.getArtist());
                album = trimOrNull(id3v2.getAlbum());
                genre = trimOrNull(id3v2.getGenreDescription());
                albumImage = id3v2.getAlbumImage();
                albumImageMimeType = id3v2.getAlbumImageMimeType();

                String yearStr = trimOrNull(id3v2.getYear());
                if (yearStr != null) {
                    try {
                        year = Integer.parseInt(yearStr.replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                }

                String trackStr = trimOrNull(id3v2.getTrack());
                if (trackStr != null) {
                    try {
                        if (trackStr.contains("/")) {
                            trackStr = trackStr.split("/")[0].trim();
                        }
                        track = Integer.parseInt(trackStr.replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                }
            } else if (mp3file.hasId3v1Tag()) {
                ID3v1 id3v1 = mp3file.getId3v1Tag();
                title = trimOrNull(id3v1.getTitle());
                artist = trimOrNull(id3v1.getArtist());
                album = trimOrNull(id3v1.getAlbum());
                genre = trimOrNull(id3v1.getGenreDescription());
                String yearStr = trimOrNull(id3v1.getYear());
                if (yearStr != null) {
                    try {
                        year = Integer.parseInt(yearStr.replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                }
                String trackStr = trimOrNull(id3v1.getTrack());
                if (trackStr != null) {
                    try {
                        track = Integer.parseInt(trackStr.replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                }
            }

            return new TagInfo(title, artist, album, track, year, genre, duration, bitrate, albumImage, albumImageMimeType);
        } catch (Exception e) {
            log.warn("Could not read ID3 tags from {}: {}", file.getAbsolutePath(), e.getMessage());
            return new TagInfo(null, null, null, null, null, null, null, null, null, null);
        }
    }

    public static List<String> parseGenres(String genreStr) {
        if (genreStr == null || genreStr.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(genreStr.split("[,/;|]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public static String formatGenresForTag(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return null;
        }
        return genres.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    public synchronized boolean updateTags(File file, String title, String artist, String album,
                                           Integer trackNumber, Integer year, List<String> genres) {
        String genreStr = formatGenresForTag(genres);
        return updateTags(file, title, artist, album, trackNumber, year, genreStr);
    }

    public synchronized boolean updateTags(File file, String title, String artist, String album,
                                           Integer trackNumber, Integer year, String genre) {
        if (file == null || !file.exists() || !file.getName().toLowerCase().endsWith(".mp3")) {
            return false;
        }

        Path originalPath = file.toPath();
        Path tempPath = null;
        try {
            Mp3File mp3file = new Mp3File(file);
            ID3v2 id3v2Tag;
            if (mp3file.hasId3v2Tag()) {
                id3v2Tag = mp3file.getId3v2Tag();
            } else {
                id3v2Tag = new ID3v24Tag();
                mp3file.setId3v2Tag(id3v2Tag);
            }

            if (title != null) id3v2Tag.setTitle(title);
            if (artist != null) id3v2Tag.setArtist(artist);
            if (album != null) id3v2Tag.setAlbum(album);
            if (trackNumber != null) id3v2Tag.setTrack(String.valueOf(trackNumber));
            if (year != null) id3v2Tag.setYear(String.valueOf(year));
            setSafeGenre(id3v2Tag, genre);

            tempPath = Files.createTempFile("mp3_tag_", ".mp3");
            mp3file.save(tempPath.toAbsolutePath().toString());

            Files.move(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Successfully updated ID3 tags for {}", file.getName());
            return true;
        } catch (Exception e) {
            log.error("Failed to update ID3 tags for file {}: {}", file.getAbsolutePath(), e.getMessage(), e);
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
            return false;
        }
    }

    private void setSafeGenre(ID3v2 id3v2Tag, String genre) {
        if (genre == null || genre.isBlank()) {
            return;
        }
        try {
            id3v2Tag.setGenreDescription(genre);
        } catch (IllegalArgumentException e) {
            // mp3agic's ID3v23Tag / AbstractID3v2Tag only recognizes standard ID3v1 genre names.
            // Custom or modern genres (e.g. "Music", "Indie Rock", "Synthwave", multi-genre) cause IllegalArgumentException.
            // ID3v2 TCON frame standardly supports arbitrary UTF-8 text.
            try {
                byte[] textBytes = genre.getBytes(StandardCharsets.UTF_8);
                byte[] frameData = new byte[textBytes.length + 1];
                frameData[0] = 3; // Encoding flag: 3 = UTF-8 in ID3v2
                System.arraycopy(textBytes, 0, frameData, 1, textBytes.length);

                ID3v2Frame frame = new ID3v2Frame("TCON", frameData);
                Map<String, ID3v2FrameSet> frameSets = id3v2Tag.getFrameSets();
                if (frameSets != null) {
                    ID3v2FrameSet frameSet = frameSets.get("TCON");
                    if (frameSet == null) {
                        frameSet = new ID3v2FrameSet("TCON");
                        frameSets.put("TCON", frameSet);
                    }
                    frameSet.clear();
                    frameSet.addFrame(frame);
                }
            } catch (Exception ex) {
                log.warn("Could not set custom genre frame for '{}': {}", genre, ex.getMessage());
                try {
                    id3v2Tag.setGenre(12); // Fallback: ID3v1 "Other"
                } catch (Exception ignored) {}
            }
        }
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
