package com.bervan.streamingapp.config.structure.mp4;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class MP4MovieRootProductionStructure extends MP4BaseRootProductionStructure {
    private List<Metadata> videos;
    private Map<String, Metadata> subtitles;
}
