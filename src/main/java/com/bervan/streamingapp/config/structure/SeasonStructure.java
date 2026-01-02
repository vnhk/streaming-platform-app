package com.bervan.streamingapp.config.structure;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SeasonStructure implements ProductionStructure {
    private Metadata seasonFolder;
    private List<EpisodeStructure> episodes;

    @Override
    public UUID getMetadataId() {
        return seasonFolder.getId();
    }

    @Override
    public String getMetadataName() {
        return seasonFolder.getFilename();
    }
}
