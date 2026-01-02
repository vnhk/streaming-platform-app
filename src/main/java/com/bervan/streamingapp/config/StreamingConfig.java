package com.bervan.streamingapp.config;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
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
        log.debug("DEBUG IF ACCESS TO SSD2");
        Path path = Paths.get("/mnt/ssd1/movies/Streaming Platform/test2.txt");
        log.info("File exist test2.txt?: " + Files.exists(path));
        path = Paths.get("/mnt/ssd1/movies/test1.txt");
        log.info("File exist test1.txt?: " + Files.exists(path));

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
                try {
                    byte[] file = fileServiceManager.readFile(metadata);
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

            List<Metadata> poster = productionEntry.getValue().get("POSTER");
            if (poster != null && !poster.isEmpty()) {
                try {
                    byte[] file = fileServiceManager.readFile(poster.get(0));
                    productionData.setBase64Src(toBase64(new ByteArrayInputStream(file)));
                } catch (Exception e) {
                    log.error("Error converting poster to base64", e);
                }
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

    private String toBase64(ByteArrayInputStream in) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        }
    }
}
