package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.common.service.OpenAIService;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.AiEnrichRequest;
import com.bervan.streamingapp.audiostreaming.model.AiEnrichResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioAiMetadataService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    private final Map<String, List<String>> genreCache = new ConcurrentHashMap<>();

    public AudioAiMetadataService() {
        this.openAIService = new OpenAIService(
                "You are an expert music metadata curator. You provide accurate music album and track metadata " +
                        "including release year, genres, descriptions, artists, and clean track titles in valid JSON format."
        );
    }

    public List<String> detectGenres(String artist, String titleOrAlbum) {
        String cleanArtist = artist != null ? artist.trim() : "";
        String cleanTitle = titleOrAlbum != null ? titleOrAlbum.trim() : "";

        if (cleanArtist.isBlank() && cleanTitle.isBlank()) {
            return List.of();
        }

        String cacheKey = (cleanArtist.toLowerCase() + "|||" + cleanTitle.toLowerCase());
        if (genreCache.containsKey(cacheKey)) {
            return genreCache.get(cacheKey);
        }

        String artistKey = cleanArtist.toLowerCase() + "|||*";
        if (cleanTitle.isBlank() && genreCache.containsKey(artistKey)) {
            return genreCache.get(artistKey);
        }

        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            log.debug("OpenAI API key missing or empty. Skipping genre detection for '{} - {}'", cleanArtist, cleanTitle);
            return List.of();
        }

        try {
            log.info("Querying AI for genres of '{} - {}'", cleanArtist, cleanTitle);
            String prompt = String.format("""
                    Identify the main music genres for artist "%s" and track/album "%s".
                    Return ONLY a JSON array with 1 to 3 genre names (e.g. ["Baroque Pop", "Art Pop", "Indie Rock"]).
                    Do NOT include markdown fences, backticks, or any additional text.
                    """, cleanArtist.isEmpty() ? "Unknown" : cleanArtist, cleanTitle.isEmpty() ? "Unknown" : cleanTitle);

            String response = openAIService.askAI(prompt, OpenAIService.GPT_4O_MINI, 0.2, openAiApiKey);
            if (response != null && !response.isBlank()) {
                String cleaned = cleanJsonResponse(response);
                List<String> parsed = objectMapper.readValue(cleaned, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                if (parsed != null && !parsed.isEmpty()) {
                    List<String> valid = parsed.stream()
                            .filter(g -> g != null && !g.isBlank() && !"Unknown".equalsIgnoreCase(g.trim()))
                            .map(String::trim)
                            .distinct()
                            .toList();
                    if (!valid.isEmpty()) {
                        genreCache.put(cacheKey, valid);
                        if (!cleanArtist.isBlank()) {
                            genreCache.putIfAbsent(artistKey, valid);
                        }
                        log.info("AI detected genres for '{} - {}': {}", cleanArtist, cleanTitle, valid);
                        return valid;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI genre detection failed for '{} - {}': {}", cleanArtist, cleanTitle, e.getMessage());
        }

        return List.of();
    }

    public AiEnrichResponse enrichAlbum(AiEnrichRequest request) {
        String artist = request.getArtist() != null ? request.getArtist().trim() : "";
        String albumTitle = request.getAlbumTitle() != null ? request.getAlbumTitle().trim() : "";
        List<String> filenames = request.getTrackFilenames() != null ? request.getTrackFilenames() : List.of();

        log.info("Requesting AI metadata enrichment for artist: '{}', album: '{}', filenames count: {}",
                artist, albumTitle, filenames.size());

        if (openAiApiKey == null || openAiApiKey.isBlank() || (artist.isBlank() && albumTitle.isBlank() && filenames.isEmpty())) {
            log.warn("OpenAI API key missing or empty request parameters. Using fallback defaults.");
            return createFallbackResponse(request);
        }

        try {
            String filenamesListStr = String.join("\n", filenames);
            String effectiveAlbum = (albumTitle.isBlank() || "Singles".equalsIgnoreCase(albumTitle))
                    ? "Unknown (determine canonical studio album from tracks)" : albumTitle;
            String prompt = String.format("""
                    Given the following music information:
                    Artist: %s
                    Album: %s
                    Track files:
                    %s

                    Identify the official canonical studio album name (e.g. for Linkin Park - Numb, the albumTitle MUST be "Meteora", not "Singles").
                    Only use "Singles" if the tracks were truly released solely as standalone non-album singles.

                    Generate a JSON object with this exact structure:
                    {
                      "albumTitle": "<canonical studio album or single title>",
                      "artist": "<main artist name>",
                      "artists": ["<primary artist>", "<collaborator/featured artist if any>"],
                      "releaseYear": <4-digit integer release year or null>,
                      "genres": ["<Genre 1>", "<Genre 2>"],
                      "description": "<brief 1-2 sentence description in Polish or English>",
                      "tracks": [
                        {
                          "originalFilename": "<original filename from the input list>",
                          "trackNumber": <1-based integer track index>,
                          "title": "<clean official song title>",
                          "artist": "<track performer/artist>"
                        }
                      ]
                    }
                    Return ONLY valid JSON without any markdown formatting or commentary.
                    """,
                    artist.isEmpty() ? "Unknown" : artist,
                    effectiveAlbum,
                    filenamesListStr.isEmpty() ? "(none provided)" : filenamesListStr
            );

            String response = openAIService.askAI(prompt, OpenAIService.GPT_4O_MINI, 0.3, openAiApiKey);
            if (response == null || response.isBlank()) {
                log.warn("AI returned empty response. Falling back to defaults.");
                return createFallbackResponse(request);
            }

            String cleanedJson = cleanJsonResponse(response);
            AiEnrichResponse result = objectMapper.readValue(cleanedJson, AiEnrichResponse.class);
            log.info("Successfully enriched metadata via AI for: {} - {}", result.getArtist(), result.getAlbumTitle());
            return result;
        } catch (Exception e) {
            log.error("AI enrichment failed for [{} - {}]: {}", artist, albumTitle, e.getMessage(), e);
            return createFallbackResponse(request);
        }
    }

    private AiEnrichResponse createFallbackResponse(AiEnrichRequest request) {
        String artist = (request.getArtist() != null && !request.getArtist().isBlank())
                ? request.getArtist().trim() : "Unknown Artist";
        String albumTitle = (request.getAlbumTitle() != null && !request.getAlbumTitle().isBlank())
                ? request.getAlbumTitle().trim() : "Unknown Album";

        List<AiEnrichResponse.TrackSuggestion> tracks = new ArrayList<>();
        if (request.getTrackFilenames() != null) {
            int trackNum = 1;
            for (String fn : request.getTrackFilenames()) {
                String cleanTitle = cleanFilename(fn);
                tracks.add(AiEnrichResponse.TrackSuggestion.builder()
                        .originalFilename(fn)
                        .trackNumber(trackNum++)
                        .title(cleanTitle)
                        .artist(artist)
                        .build());
            }
        }

        return AiEnrichResponse.builder()
                .artist(artist)
                .albumTitle(albumTitle)
                .artists(List.of(artist))
                .releaseYear(Year.now().getValue())
                .genres(List.of("Unknown"))
                .description("")
                .tracks(tracks)
                .build();
    }

    private String cleanFilename(String fn) {
        if (fn == null) return "Unknown Track";
        String name = fn;
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) name = name.substring(0, lastDot);
        // Remove leading track numbers like "01 - " or "01. "
        name = name.replaceFirst("^\\d+\\s*[-._]\\s*", "");
        return name.trim();
    }

    private String cleanJsonResponse(String response) {
        String json = response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }

    public Map<Integer, TrackInfoSuggestion> resolveBatchTracks(List<TrackResolveQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return Map.of();
        }

        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            log.warn("OpenAI API key is missing or empty. Skipping batch AI track resolution.");
            return Map.of();
        }

        Map<Integer, TrackInfoSuggestion> results = new HashMap<>();
        int chunkSize = 20;

        for (int i = 0; i < queries.size(); i += chunkSize) {
            List<TrackResolveQuery> chunk = queries.subList(i, Math.min(i + chunkSize, queries.size()));
            try {
                StringBuilder listBuilder = new StringBuilder();
                for (TrackResolveQuery q : chunk) {
                    listBuilder.append(String.format(
                            "{\"id\": %d, \"filename\": \"%s\", \"artist\": \"%s\", \"title\": \"%s\", \"album\": \"%s\"}\n",
                            q.getId(),
                            escapeJson(q.getFilename()),
                            escapeJson(q.getTaggedArtist() != null ? q.getTaggedArtist() : ""),
                            escapeJson(q.getTaggedTitle() != null ? q.getTaggedTitle() : ""),
                            escapeJson(q.getTaggedAlbum() != null ? q.getTaggedAlbum() : "")
                    ));
                }

                String prompt = String.format("""
                        Given the following music tracks, identify their official artist name, canonical studio album name that the track originally appeared on (for example, for Linkin Park - Numb, the album MUST be "Meteora"; only return "Singles" if the song was truly an independent standalone single never released on a studio album), clean official song title, release year (4-digit integer), and 1 to 3 music genres.
                        Return ONLY a valid JSON array of objects with keys: id (number), artist (string), album (string), title (string), releaseYear (number or null), genres (array of strings).
                        Do NOT include markdown formatting, backticks, or any explanation.

                        Input tracks:
                        %s
                        """, listBuilder.toString());

                String response = openAIService.askAI(prompt, OpenAIService.GPT_4O_MINI, 0.2, openAiApiKey);
                if (response != null && !response.isBlank()) {
                    String cleaned = cleanJsonResponse(response);
                    List<TrackInfoSuggestion> parsed = objectMapper.readValue(
                            cleaned,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, TrackInfoSuggestion.class)
                    );
                    if (parsed != null) {
                        for (TrackInfoSuggestion s : parsed) {
                            if (s != null) {
                                results.put(s.getId(), s);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve batch tracks via AI for chunk starting at {}: {}", i, e.getMessage());
            }
        }

        return results;
    }

    public TrackInfoSuggestion resolveSingleTrack(String artist, String title, String album, String filename) {
        TrackResolveQuery query = TrackResolveQuery.builder()
                .id(1)
                .filename(filename)
                .taggedArtist(artist)
                .taggedTitle(title)
                .taggedAlbum(album)
                .build();
        Map<Integer, TrackInfoSuggestion> map = resolveBatchTracks(List.of(query));
        return map.get(1);
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackResolveQuery {
        private int id;
        private String filename;
        private String taggedArtist;
        private String taggedTitle;
        private String taggedAlbum;
        private Integer taggedYear;
        private String taggedGenre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackInfoSuggestion {
        private int id;
        private String artist;
        private String album;
        private String title;
        private Integer releaseYear;
        @Builder.Default
        private List<String> genres = new ArrayList<>();
    }
}

