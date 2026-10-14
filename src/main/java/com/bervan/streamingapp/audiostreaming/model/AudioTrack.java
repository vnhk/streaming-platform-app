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
public class AudioTrack {
    private String id;
    private String metadataId;
    private String title;
    private String artist;
    @Builder.Default
    private List<String> artists = new ArrayList<>();
    private String album;
    private String albumArtist;
    private Integer trackNumber;
    private Integer discNumber;
    private Double durationSeconds;
    private Integer year;
    private String genre;
    @Builder.Default
    private List<String> genres = new ArrayList<>();
    private String filename;
    private String relativePath;
    private Long fileSize;
    private String format;
    private Integer bitrate;
    private boolean hasTagMismatch;
    private String tagMismatchDetails;
}
