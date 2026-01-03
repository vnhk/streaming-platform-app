package com.bervan.streamingapp.config.structure.hls;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class HLSTvSeriesRootProductionStructure extends HLSBaseRootProductionStructure {
    private List<HLSSeasonStructure> seasons;
}
