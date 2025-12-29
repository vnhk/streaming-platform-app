package com.bervan.streamingapp.conifg;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class StreamingConfig {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    private final FileServiceManager fileServiceManager;
    private final VideoManager videoManager;

    public StreamingConfig(FileServiceManager fileServiceManager, VideoManager videoManager) {
        this.fileServiceManager = fileServiceManager;
        this.videoManager = videoManager;
    }

    @Bean
    public Map<String, ProductionData> streamingProductionData() {
        Map<String, ProductionData> result = new HashMap<>();
        log.info("Loading all productions");
        long start = System.currentTimeMillis();
        Map<Metadata, Map<String, List<Metadata>>> allProductions = loadAllProductions();

        for (Map.Entry<Metadata, Map<String, List<Metadata>>> productionEntry : allProductions.entrySet()) {
            ProductionData productionData = new ProductionData();
            productionData.setMainFolder(productionEntry.getKey());
            productionData.setProductionId(productionEntry.getKey().getId().toString());
            productionData.setProductionFolders(productionEntry.getValue());
            List<Metadata> details = productionEntry.getValue().get("DETAILS");
            ProductionDetails productionDetails;
            if (details != null && !details.isEmpty()) {
                Metadata metadata = details.get(0);
                byte[] file = fileServiceManager.readFile(metadata);
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    String jsonString = new String(file, StandardCharsets.UTF_8);
                    productionDetails = objectMapper.readValue(jsonString, ProductionDetails.class);
                    productionData.setProductionName(productionDetails.getName());
                    productionData.setProductionDetails(productionDetails);
                } catch (Exception e) {
                    log.error("Error parsing JSON to ProductionDetails", e);
                    continue;
                }
            } else {
                log.error("Details file is missing for production " + productionEntry.getKey().getPath());
                continue;
            }
            result.put(productionData.getProductionName(), productionData);
        }

        long end = System.currentTimeMillis();
        log.info("Loading all productions finished in {} ms", end - start);

        return result;
    }

    private Map<Metadata, Map<String, List<Metadata>>> loadAllProductions() {
        Map<Metadata, Map<String, List<Metadata>>> allVideos = new HashMap<>();
        List<Metadata> allVideosFolders = videoManager.loadVideosMainDirectories();
        for (Metadata mainVideoFolder : allVideosFolders) {
            allVideos.put(mainVideoFolder, videoManager.loadVideoDirectoryContent(mainVideoFolder));
        }

        return allVideos;
    }
}
