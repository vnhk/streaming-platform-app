package com.bervan.streamingapp.audiostreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTrackRequest {
    private String title;
    private String artist;
    private List<String> artists;
    private Integer trackNumber;
    private Integer year;
    private String genre;
    private List<String> genres;
}
