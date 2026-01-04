package com.bervan.streamingapp.config;

import com.bervan.filestorage.model.Metadata;
import com.bervan.streamingapp.config.structure.ProductionFileType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetadataByPathAndType extends HashMap<String, Map<ProductionFileType, List<Metadata>>> {
}
