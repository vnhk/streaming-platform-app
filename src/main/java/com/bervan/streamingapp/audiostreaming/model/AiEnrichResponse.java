package com.bervan.streamingapp.audiostreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEnrichResponse {
    private String albumTitle;
    private String artist;
    @Builder.Default
    private List<String> artists = new ArrayList<>();
    private Integer releaseYear;
    @Builder.Default
    private List<String> genres = new ArrayList<>();
    private String description;
    @Builder.Default
    private List<TrackSuggestion> tracks = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackSuggestion {
        private String originalFilename;
        private Integer trackNumber;
        private String title;
        private String artist;
    }
}
