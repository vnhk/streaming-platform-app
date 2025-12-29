package com.bervan.streamingapp.config;

import com.bervan.filestorage.model.Metadata;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProductionData {
    private String productionName;
    private String productionId;
    private Metadata mainFolder;
    private Map<String, List<Metadata>> productionFolders;
    private ProductionDetails productionDetails;
}
