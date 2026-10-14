package com.bervan.streamingapp.audiostreaming.service;

import com.bervan.common.service.OpenAIService;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.audiostreaming.model.AiEnrichRequest;
import com.bervan.streamingapp.audiostreaming.model.AiEnrichResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudioAiMetadataService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "audio-streaming");
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    public AudioAiMetadataService() {
        this.openAIService = new OpenAIService(
                "You are an expert music metadata curator. You provide accurate music album and track metadata " +
                        "including release year, genres, descriptions, artists, and clean track titles in valid JSON format."
        );
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
            String prompt = String.format("""
                    Given the following music information:
                    Artist: %s
                    Album: %s
                    Track files:
                    %s

                    Generate a JSON object with this exact structure:
                    {
                      "albumTitle": "<official or cleaned album title>",
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
                    albumTitle.isEmpty() ? "Unknown" : albumTitle,
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
}
