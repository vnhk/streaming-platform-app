package com.bervan.streamingapp.config.structure;

import lombok.Data;

import java.util.List;

@Data
public abstract class SeasonStructure implements ProductionStructure {

    public abstract List<? extends EpisodeStructure> getEpisodes();
}
