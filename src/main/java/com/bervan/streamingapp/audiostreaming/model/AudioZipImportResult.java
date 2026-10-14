package com.bervan.streamingapp.audiostreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioZipImportResult {
    private int importedTracksCount;
    private int albumsCount;
    private List<String> affectedAlbumTitles;
    private AudioAlbum primaryAlbum;
    private String message;
}
