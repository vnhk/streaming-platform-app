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
public class CreateAlbumRequest {
    private String title;
    private String artist;
    private List<String> artists;
    private Integer releaseYear;
    private List<String> genres;
    private String description;
    private boolean isSingle;
}
