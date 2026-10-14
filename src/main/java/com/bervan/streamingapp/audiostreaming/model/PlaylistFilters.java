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
public class PlaylistFilters {
    @Builder.Default
    private List<String> genres = new ArrayList<>();
    @Builder.Default
    private List<String> artists = new ArrayList<>();
    private String query;
    private Integer yearFrom;
    private Integer yearTo;
}
