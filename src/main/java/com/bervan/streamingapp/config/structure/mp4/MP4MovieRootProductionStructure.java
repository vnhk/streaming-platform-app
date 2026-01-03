package com.bervan.streamingapp.config.structure.mp4;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.MovieBaseRootProductionStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MP4MovieRootProductionStructure extends MovieBaseRootProductionStructure {
    private List<Metadata> videosFolders;
}
