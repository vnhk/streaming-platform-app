package com.bervan.streamingapp.config.structure.hls;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.MovieBaseRootProductionStructure;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class HLSMovieRootProductionStructure extends MovieBaseRootProductionStructure {
    @Override
    public List<Metadata> getVideosFolders() {
        return List.of(mainFolder);
    }
}
