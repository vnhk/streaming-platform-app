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
public class AudioPlaylist {
    private String id;
    private String name;
    private String description;
    private String createdAt;
    private String updatedAt;
    @Builder.Default
    private PlaylistFilters filters = new PlaylistFilters();
    @Builder.Default
    private List<String> trackMetadataIds = new ArrayList<>();
    @Builder.Default
    private List<AudioTrack> tracks = new ArrayList<>();
    private int trackCount;
    private double totalDurationSeconds;
}
