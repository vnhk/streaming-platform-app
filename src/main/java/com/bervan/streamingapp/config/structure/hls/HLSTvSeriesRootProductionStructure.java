package com.bervan.streamingapp.config.structure.hls;

import com.bervan.streamingapp.config.structure.TvSeriesBaseRootProductionStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class HLSTvSeriesRootProductionStructure extends TvSeriesBaseRootProductionStructure {
    private List<HLSSeasonStructure> seasons;
}
