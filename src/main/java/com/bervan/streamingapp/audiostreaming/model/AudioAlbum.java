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
public class AudioAlbum {
    private String id;
    private String title;
    private String artist;
    @Builder.Default
    private List<String> artists = new ArrayList<>();
    private Integer releaseYear;
    @Builder.Default
    private List<String> genres = new ArrayList<>();
    private String description;
    private String coverFilename;
    private boolean hasCover;
    private String folderPath;
    @Builder.Default
    private List<AudioTrack> tracks = new ArrayList<>();
    private Double totalDurationSeconds;
    private Integer trackCount;
    private boolean isSingle;
    private boolean hasTagMismatch;
}
