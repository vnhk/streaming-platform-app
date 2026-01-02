package com.bervan.streamingapp.config.structure;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;

import java.util.UUID;

@Data
public abstract class BaseRootProductionStructure implements ProductionStructure {
    protected Metadata mainFolder;
    protected Metadata poster;
    protected Metadata details;

    @Override
    public UUID getMetadataId() {
        return mainFolder.getId();
    }

    @Override
    public String getMetadataName() {
        return mainFolder.getFilename();
    }
}
