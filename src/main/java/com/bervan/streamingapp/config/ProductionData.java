package com.bervan.streamingapp.config;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.BaseRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4BaseRootProductionStructure;
import lombok.Data;

@Data
public class ProductionData {
    private String productionName;
    private String productionId;
    private Metadata mainFolder;
    private String mainFolderPath;
    private MetadataByPathAndType productionFoldersByPathAndType;
    private BaseRootProductionStructure productionStructure;
    private ProductionDetails productionDetails;
    private String base64PosterSrc;
}
