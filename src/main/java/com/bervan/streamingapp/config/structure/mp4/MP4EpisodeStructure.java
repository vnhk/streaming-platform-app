package com.bervan.streamingapp.config.structure.mp4;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.EpisodeStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class MP4EpisodeStructure extends EpisodeStructure {
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
