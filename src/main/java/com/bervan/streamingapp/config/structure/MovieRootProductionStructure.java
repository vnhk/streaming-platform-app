package com.bervan.streamingapp.config.structure;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class MovieRootProductionStructure extends BaseRootProductionStructure {
    private List<Metadata> videos;
    private Map<String, Metadata> subtitles;
}
