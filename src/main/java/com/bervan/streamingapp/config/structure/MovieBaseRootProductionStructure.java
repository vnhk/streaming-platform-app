package com.bervan.streamingapp.config.structure;

import com.bervan.filestorage.model.Metadata;

import java.util.List;

public abstract class MovieBaseRootProductionStructure extends BaseRootProductionStructure {
    public abstract List<Metadata> getVideosFolders();
}
