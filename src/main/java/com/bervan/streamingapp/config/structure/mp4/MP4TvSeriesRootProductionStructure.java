package com.bervan.streamingapp.config.structure.mp4;

import com.bervan.streamingapp.config.structure.TvSeriesBaseRootProductionStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MP4TvSeriesRootProductionStructure extends TvSeriesBaseRootProductionStructure {
    private List<MP4SeasonStructure> seasons;
}
