package com.bervan.streamingapp.config.structure.mp4;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MP4TvSeriesRootProductionStructure extends MP4BaseRootProductionStructure {
    private List<MP4SeasonStructure> seasons;
}
