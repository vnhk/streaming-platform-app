package com.bervan.streamingapp.config.structure;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class EpisodeStructure implements ProductionStructure {
    private Metadata episodeFolder;
    private Map<String, Metadata> subtitles;
    private Metadata video;
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
