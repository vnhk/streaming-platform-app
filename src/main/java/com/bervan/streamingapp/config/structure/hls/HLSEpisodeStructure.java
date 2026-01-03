package com.bervan.streamingapp.config.structure.hls;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.EpisodeStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class HLSEpisodeStructure extends EpisodeStructure {
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
