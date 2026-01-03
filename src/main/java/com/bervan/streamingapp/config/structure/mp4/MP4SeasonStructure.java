package com.bervan.streamingapp.config.structure.mp4;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.SeasonStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class MP4SeasonStructure extends SeasonStructure {
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
