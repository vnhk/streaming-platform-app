package com.bervan.streamingapp.config.structure.hls;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.mp4.MP4ProductionStructure;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class HLSSeasonStructure implements HLSProductionStructure {
    private Metadata seasonFolder;
    private List<HLSEpisodeStructure> episodes;

    @Override
    public UUID getMetadataId() {
        return seasonFolder.getId();
    }

    @Override
    public String getMetadataName() {
        return seasonFolder.getFilename();
    }
}
