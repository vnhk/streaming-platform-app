package com.bervan.streamingapp.config.structure.mp4;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class MP4SeasonStructure implements MP4ProductionStructure {
    private Metadata seasonFolder;
    private List<MP4EpisodeStructure> episodes;

    @Override
    public UUID getMetadataId() {
        return seasonFolder.getId();
    }

    @Override
    public String getMetadataName() {
        return seasonFolder.getFilename();
    }
}
