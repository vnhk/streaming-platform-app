package com.bervan.streamingapp.config.structure;

import java.util.List;

public abstract class TvSeriesBaseRootProductionStructure extends BaseRootProductionStructure {
    public abstract List<? extends SeasonStructure> getSeasons();

}
