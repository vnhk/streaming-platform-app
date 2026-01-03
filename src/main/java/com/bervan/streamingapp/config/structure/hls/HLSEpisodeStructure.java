package com.bervan.streamingapp.config.structure.hls;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;

import java.util.UUID;

@Data
public class HLSEpisodeStructure implements HLSProductionStructure {
    private Metadata episodeFolder;
    private Metadata poster;

    @Override
    public UUID getMetadataId() {
        return episodeFolder.getId();
    }

    @Override
    public String getMetadataName() {
        return episodeFolder.getFilename();
    }
}
