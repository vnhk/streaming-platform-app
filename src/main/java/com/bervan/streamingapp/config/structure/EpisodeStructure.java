package com.bervan.streamingapp.config.structure;

import com.bervan.filestorage.model.Metadata;

public abstract class EpisodeStructure implements ProductionStructure {
    public abstract Metadata getPoster();

    public abstract Metadata getEpisodeFolder();
}
