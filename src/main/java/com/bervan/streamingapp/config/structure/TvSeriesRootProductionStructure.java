package com.bervan.streamingapp.config.structure;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TvSeriesRootProductionStructure extends BaseRootProductionStructure {
    private List<SeasonStructure> seasons;
}
