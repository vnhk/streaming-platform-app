package com.bervan.streamingapp.config;

import com.bervan.common.ImageScaleUtility;
import com.bervan.common.service.OpenAIService;
import com.bervan.filestorage.model.BervanMockMultiPartFile;
import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.model.UploadResponse;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.config.structure.BaseRootProductionStructure;
import com.bervan.streamingapp.config.structure.ProductionFileType;
import com.bervan.streamingapp.config.structure.hls.HLSEpisodeStructure;
import com.bervan.streamingapp.config.structure.hls.HLSMovieRootProductionStructure;
import com.bervan.streamingapp.config.structure.hls.HLSSeasonStructure;
import com.bervan.streamingapp.config.structure.hls.HLSTvSeriesRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4EpisodeStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4MovieRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4SeasonStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4TvSeriesRootProductionStructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class StreamingConfigLoader {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final FileServiceManager fileServiceManager;
    private final VideoManager videoManager;
    private final OpenAIService openAIService;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    public StreamingConfigLoader(FileServiceManager fileServiceManager, VideoManager videoManager) {
        this.fileServiceManager = fileServiceManager;
        this.videoManager = videoManager;
        this.openAIService = new OpenAIService(
                "You are a helpful assistant that generates movie and TV series metadata in JSON format. " +
                        "Provide accurate information based on the title given."
        );
    }

    public Map<String, ProductionData> getStringProductionDataMap() {
        Map<String, ProductionData> result = new HashMap<>();
        log.info("Loading all productions");
        long start = System.currentTimeMillis();
        Map<Metadata, MetadataByPathAndType> allProductions = loadAllProductionsMetadata();

        for (Map.Entry<Metadata, MetadataByPathAndType> productionEntry : allProductions.entrySet()) {
            ProductionData productionData = buildProductionData(productionEntry.getKey(), productionEntry.getValue());
            if (productionData != null && productionData.getProductionName() != null) {
                result.put(productionData.getProductionName(), productionData);
            }
        }

        long end = System.currentTimeMillis();
        log.info("Loading all productions finished in {} ms", end - start);
        log.info("Loaded Productions: [{}]", String.join(", ", result.keySet()));
        return result;
    }

    public ProductionData loadSingleProduction(Metadata mainFolder) {
        log.info("Loading single production for folder: {}", mainFolder.getPath() + mainFolder.getFilename());
        MetadataByPathAndType productionFolders = videoManager.loadVideoDirectoryContent(mainFolder);
        return buildProductionData(mainFolder, productionFolders);
    }

    public ProductionData buildProductionData(Metadata mainFolder, MetadataByPathAndType productionFolders) {
        ProductionData productionData = new ProductionData();
        productionData.setMainFolder(mainFolder);
        String mainFolderPath = (mainFolder.getPath() + mainFolder.getFilename() + File.separator).trim();
        log.info("Building production's data :{}", mainFolderPath);
        productionData.setMainFolderPath(mainFolderPath);
        productionData.setProductionId(mainFolder.getId().toString());
        productionData.setProductionFoldersByPathAndType(productionFolders);

        Map<ProductionFileType, List<Metadata>> mainFolderMap = productionFolders.get(mainFolderPath);
        if (mainFolderMap == null) {
            mainFolderMap = new HashMap<>();
            productionFolders.put(mainFolderPath, mainFolderMap);
        }

        List<Metadata> details = mainFolderMap.get(ProductionFileType.DETAILS);
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
                return null;
            }
        } else {
            log.warn("Details file does not exist for production: {}. Generating with AI...", mainFolderPath);
            try {
                productionDetails = generateProductionDetails(mainFolder, productionFolders, mainFolderPath);
                if (productionDetails != null) {
                    productionData.setProductionName(productionDetails.getName());
                    productionData.setProductionDetails(productionDetails);
                    Metadata savedDetails = saveDetailsToFile(mainFolder, mainFolderPath, productionDetails);
                    if (savedDetails != null) {
                        mainFolderMap.computeIfAbsent(ProductionFileType.DETAILS, k -> new ArrayList<>()).add(savedDetails);
                    }
                } else {
                    log.error("Failed to generate production details for: {}", mainFolderPath);
                    return null;
                }
            } catch (Exception e) {
                log.error("Error generating ProductionDetails with AI", e);
                return null;
            }
        }

        loadMainPosterSrc(productionFolders, mainFolderPath, productionData);
        loadProductionStructure(productionData, productionFolders);

        return productionData;
    }

    private void loadProductionStructure(ProductionData productionData, MetadataByPathAndType productionFolders) {
        String productionName = productionData.getProductionName();
        try {
            log.info("[{}] Loading production structure (format={})", productionName,
                    productionData.getProductionDetails().getVideoFormat());
            if (productionData.getProductionDetails().getVideoFormat() == ProductionDetails.VideoFormat.HLS) {
                loadHLSProductionStructure(productionData, productionFolders);
            } else {
                loadMP4ProductionStructure(productionData, productionFolders);
            }
            log.info("[{}] Production structure loaded successfully", productionName);
        } catch (Exception e) {
            log.error("[{}] Error loading production structure: {}", productionName, e.getMessage(), e);
        }
    }

    private void loadHLSProductionStructure(ProductionData productionData, MetadataByPathAndType productionFolders) {
        String productionName = productionData.getProductionName();
        BaseRootProductionStructure rootProductionStructure;
        if (productionData.getProductionDetails().getType() == ProductionDetails.VideoType.TV_SERIES) {
            rootProductionStructure = new HLSTvSeriesRootProductionStructure();
            Map<ProductionFileType, List<Metadata>> seasonsMap = productionFolders.get(productionData.getMainFolderPath());
            List<Metadata> seasonDirectories = seasonsMap != null ? seasonsMap.get(ProductionFileType.DIRECTORY) : null;
            List<HLSSeasonStructure> seasonStructureList = new ArrayList<>();
            if (seasonDirectories == null || seasonDirectories.isEmpty()) {
                log.warn("[{}] No season directories found at {}", productionName, productionData.getMainFolderPath());
            } else {
                log.info("[{}] Found {} season(s)", productionName, seasonDirectories.size());
                for (Metadata seasonDirectory : seasonDirectories) {
                    String seasonName = seasonDirectory.getFilename();
                    try {
                        log.info("[{}][{}] Processing season", productionName, seasonName);
                        HLSSeasonStructure seasonStructure = new HLSSeasonStructure();
                        seasonStructure.setSeasonFolder(seasonDirectory);
                        String seasonPath = seasonDirectory.getPath() + seasonDirectory.getFilename() + File.separator;
                        Map<ProductionFileType, List<Metadata>> seasonsFilesMap = productionFolders.get(seasonPath);
                        if (seasonsFilesMap == null) {
                            log.warn("[{}][{}] No files map found for season path: {}", productionName, seasonName, seasonPath);
                            seasonStructureList.add(seasonStructure);
                            continue;
                        }
                        List<Metadata> episodeDirectories = seasonsFilesMap.get(ProductionFileType.DIRECTORY);
                        List<HLSEpisodeStructure> episodeStructureList = new ArrayList<>();
                        if (episodeDirectories == null || episodeDirectories.isEmpty()) {
                            log.warn("[{}][{}] No episode directories found", productionName, seasonName);
                        } else {
                            log.info("[{}][{}] Found {} episode(s)", productionName, seasonName, episodeDirectories.size());
                            for (Metadata episodeDirectory : episodeDirectories) {
                                String episodeName = episodeDirectory.getFilename();
                                try {
                                    HLSEpisodeStructure episodeStructure = new HLSEpisodeStructure();
                                    episodeStructure.setEpisodeFolder(episodeDirectory);
                                    String episodePath = episodeDirectory.getPath() + episodeDirectory.getFilename() + File.separator;
                                    Map<ProductionFileType, List<Metadata>> episodeFilesMap = productionFolders.get(episodePath);
                                    if (episodeFilesMap == null) {
                                        log.warn("[{}][{}][{}] No files map found for episode path: {}", productionName, seasonName, episodeName, episodePath);
                                    } else {
                                        List<Metadata> poster = episodeFilesMap.get(ProductionFileType.POSTER);
                                        if (poster != null && !poster.isEmpty()) {
                                            episodeStructure.setPoster(poster.get(0));
                                        } else {
                                            log.debug("[{}][{}][{}] No poster found", productionName, seasonName, episodeName);
                                        }
                                    }
                                    episodeStructureList.add(episodeStructure);
                                } catch (Exception e) {
                                    log.error("[{}][{}][{}] Error processing episode: {}", productionName, seasonName, episodeName, e.getMessage(), e);
                                }
                            }
                        }
                        seasonStructure.setEpisodes(episodeStructureList);
                        seasonStructureList.add(seasonStructure);
                    } catch (Exception e) {
                        log.error("[{}][{}] Error processing season: {}", productionName, seasonName, e.getMessage(), e);
                    }
                }
            }
            ((HLSTvSeriesRootProductionStructure) rootProductionStructure).setSeasons(seasonStructureList);
        } else {
            rootProductionStructure = new HLSMovieRootProductionStructure();
        }
        updateRoot(productionData, productionFolders, rootProductionStructure);

        productionData.setProductionStructure(rootProductionStructure);
    }


    private void updateRoot(ProductionData productionData, MetadataByPathAndType productionFolders, BaseRootProductionStructure rootProductionStructure) {
        rootProductionStructure.setMainFolder(productionData.getMainFolder());
        Map<ProductionFileType, List<Metadata>> mainFolderMap = productionFolders.get(productionData.getMainFolderPath());
        if (mainFolderMap != null) {
            List<Metadata> details = mainFolderMap.get(ProductionFileType.DETAILS);
            if (details != null && !details.isEmpty()) {
                rootProductionStructure.setDetails(details.get(0));
            }
            List<Metadata> poster = mainFolderMap.get(ProductionFileType.POSTER);
            if (poster != null && !poster.isEmpty()) {
                rootProductionStructure.setPoster(poster.get(0));
            }
        }
    }

    private void loadMP4ProductionStructure(ProductionData productionData, MetadataByPathAndType productionFolders) {
        String productionName = productionData.getProductionName();
        BaseRootProductionStructure rootProductionStructure;
        if (productionData.getProductionDetails().getType() == ProductionDetails.VideoType.TV_SERIES) {
            rootProductionStructure = new MP4TvSeriesRootProductionStructure();
            Map<ProductionFileType, List<Metadata>> seasonsMap = productionFolders.get(productionData.getMainFolderPath());
            List<Metadata> seasonDirectories = seasonsMap != null ? seasonsMap.get(ProductionFileType.DIRECTORY) : null;
            List<MP4SeasonStructure> seasonStructureList = new ArrayList<>();
            if (seasonDirectories == null || seasonDirectories.isEmpty()) {
                log.warn("[{}] No season directories found at {}", productionName, productionData.getMainFolderPath());
            } else {
                log.info("[{}] Found {} season(s)", productionName, seasonDirectories.size());
                for (Metadata seasonDirectory : seasonDirectories) {
                    String seasonName = seasonDirectory.getFilename();
                    try {
                        log.info("[{}][{}] Processing season", productionName, seasonName);
                        MP4SeasonStructure seasonStructure = new MP4SeasonStructure();
                        seasonStructure.setSeasonFolder(seasonDirectory);
                        String seasonPath = seasonDirectory.getPath() + seasonDirectory.getFilename() + File.separator;
                        Map<ProductionFileType, List<Metadata>> seasonsFilesMap = productionFolders.get(seasonPath);
                        if (seasonsFilesMap == null) {
                            log.warn("[{}][{}] No files map found for season path: {}", productionName, seasonName, seasonPath);
                            seasonStructureList.add(seasonStructure);
                            continue;
                        }
                        List<Metadata> episodeDirectories = seasonsFilesMap.get(ProductionFileType.DIRECTORY);
                        List<MP4EpisodeStructure> episodeStructureList = new ArrayList<>();
                        if (episodeDirectories == null || episodeDirectories.isEmpty()) {
                            log.warn("[{}][{}] No episode directories found", productionName, seasonName);
                        } else {
                            log.info("[{}][{}] Found {} episode(s)", productionName, seasonName, episodeDirectories.size());
                            for (Metadata episodeDirectory : episodeDirectories) {
                                String episodeName = episodeDirectory.getFilename();
                                try {
                                    MP4EpisodeStructure episodeStructure = new MP4EpisodeStructure();
                                    episodeStructure.setEpisodeFolder(episodeDirectory);
                                    String episodePath = episodeDirectory.getPath() + episodeDirectory.getFilename() + File.separator;
                                    Map<ProductionFileType, List<Metadata>> episodeFilesMap = productionFolders.get(episodePath);
                                    if (episodeFilesMap == null) {
                                        log.warn("[{}][{}][{}] No files map found for episode path: {}", productionName, seasonName, episodeName, episodePath);
                                    } else {
                                        List<Metadata> poster = episodeFilesMap.get(ProductionFileType.POSTER);
                                        if (poster != null && !poster.isEmpty()) {
                                            episodeStructure.setPoster(poster.get(0));
                                        } else {
                                            log.debug("[{}][{}][{}] No poster found", productionName, seasonName, episodeName);
                                        }

                                        List<Metadata> video = episodeFilesMap.get(ProductionFileType.VIDEO);
                                        if (video != null && !video.isEmpty()) {
                                            episodeStructure.setVideo(video.get(0));
                                        } else {
                                            log.warn("[{}][{}][{}] No video file found", productionName, seasonName, episodeName);
                                        }

                                        List<Metadata> subtitles = episodeFilesMap.get(ProductionFileType.SUBTITLE);
                                        episodeStructure.setSubtitles(getSubtitlesMap(subtitles));
                                    }
                                    episodeStructureList.add(episodeStructure);
                                } catch (Exception e) {
                                    log.error("[{}][{}][{}] Error processing episode: {}", productionName, seasonName, episodeName, e.getMessage(), e);
                                }
                            }
                        }
                        seasonStructure.setEpisodes(episodeStructureList);
                        seasonStructureList.add(seasonStructure);
                    } catch (Exception e) {
                        log.error("[{}][{}] Error processing season: {}", productionName, seasonName, e.getMessage(), e);
                    }
                }
            }
            ((MP4TvSeriesRootProductionStructure) rootProductionStructure).setSeasons(seasonStructureList);
        } else {
            rootProductionStructure = new MP4MovieRootProductionStructure();
            ((MP4MovieRootProductionStructure) rootProductionStructure).setVideosFolders(List.of(productionData.getMainFolder()));
        }
        updateRoot(productionData, productionFolders, rootProductionStructure);

        productionData.setProductionStructure(rootProductionStructure);
    }


    private ProductionDetails generateProductionDetails(Metadata mainFolder, MetadataByPathAndType productionFolders, String mainFolderPath) {
        String folderName = mainFolder.getFilename();

        // Determine if it's a TV series or movie based on season folders
        boolean isTvSeries = false;
        Map<ProductionFileType, List<Metadata>> mainFolderContent = productionFolders.get(mainFolderPath);
        if (mainFolderContent != null) {
            List<Metadata> directories = mainFolderContent.get(ProductionFileType.DIRECTORY);
            if (directories != null) {
                for (Metadata dir : directories) {
                    String dirName = dir.getFilename();
                    if (dirName.matches("(?i)^(S\\d+|Season\\s*\\d+).*")) {
                        isTvSeries = true;
                        break;
                    }
                }
            }
        }

        String videoType = isTvSeries ? "TV series" : "movie";

        String prompt = String.format(
                "Based on the title '%s' which is a %s, generate a JSON object with the following structure:\n" +
                        "{\n" +
                        "  \"name\": \"<full title>\",\n" +
                        "  \"description\": \"<brief description in English>\",\n" +
                        "  \"type\": \"%s\",\n" +
                        "  \"audioLang\": [\"<list of audio languages, e.g., EN, PL - use original audio language ex. for US movies use 'EN', for PL movies use 'PL'>\"],\n" +
                        "  \"releaseYearStart\": <year>,\n" +
                        "  \"releaseYearEnd\": <year or null for movies and tv series in progress>,\n" +
                        "  \"categories\": [\"<list of categories, e.g., [\"Action\", \"Adventure\", \"Drama\", \"Historical\"]>\"],\n" +
                        "  \"tags\": [\"<list of tags example: [\"vikings\", \"norse mythology\", \"raids\", \"warriors\", \"power struggles\"]>\"],\n" +
                        "  \"country\": \"<country of origin ex. Canada>\",\n" +
                        "  \"rating\": <rating from 0-10 example 8.5>,\n" +
                        "  \"videoFormat\": \"hls\"\n" +
                        "}\n" +
                        "Return ONLY the JSON object, no additional text.",
                folderName,
                videoType,
                isTvSeries ? "tv_series" : "movie"
        );

        log.info("Asking AI to generate details for: {}", folderName);
        String aiResponse = openAIService.askAI(prompt, OpenAIService.GPT_4O_MINI, 0.3, openAiApiKey);

        if (aiResponse == null || aiResponse.isBlank()) {
            log.error("AI returned null or empty response for: {}", folderName);
            return null;
        }

        try {
            // Clean up the response to extract JSON if wrapped in markdown
            String jsonString = aiResponse.trim();
            if (jsonString.startsWith("```json")) {
                jsonString = jsonString.substring(7);
            }
            if (jsonString.startsWith("```")) {
                jsonString = jsonString.substring(3);
            }
            if (jsonString.endsWith("```")) {
                jsonString = jsonString.substring(0, jsonString.length() - 3);
            }
            jsonString = jsonString.trim();

            ObjectMapper objectMapper = new ObjectMapper();
            ProductionDetails details = objectMapper.readValue(jsonString, ProductionDetails.class);
            log.info("Successfully generated production details for: {}", folderName);
            return details;
        } catch (Exception e) {
            log.error("Error parsing AI response to ProductionDetails for: {}", folderName, e);
            log.error("AI Response was: {}", aiResponse);
            return null;
        }
    }

    private Metadata saveDetailsToFile(Metadata mainFolder, String mainFolderPath, ProductionDetails productionDetails) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(productionDetails);

            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);

            // Save the file to the production folder
            String detailsFileName = "details.json";
            UploadResponse uploadResponse = fileServiceManager.save(
                    new BervanMockMultiPartFile(
                            detailsFileName,
                            detailsFileName,
                            "application/json",
                            new ByteArrayInputStream(jsonBytes)
                    ),
                    "Auto-generated production details",
                    mainFolderPath
            );

            log.info("Successfully saved details.json for: {}", mainFolderPath);
            if (uploadResponse != null && uploadResponse.getMetadata() != null && !uploadResponse.getMetadata().isEmpty()) {
                return uploadResponse.getMetadata().get(0);
            }
        } catch (Exception e) {
            log.error("Error saving details.json for: {}", mainFolderPath, e);
        }
        return null;
    }

    private Map<String, Metadata> getSubtitlesMap(List<Metadata> subtitles) {
        if (subtitles == null || subtitles.isEmpty()) {
            return new HashMap<>();
        }
        Optional<Metadata> enSubtitle = videoManager.getSubtitle(VideoManager.EN, subtitles);
        Optional<Metadata> plSubtitle = videoManager.getSubtitle(VideoManager.PL, subtitles);
        Optional<Metadata> esSubtitle = videoManager.getSubtitle(VideoManager.ES, subtitles);
        Map<String, Metadata> subtitlesMap = new HashMap<>();
        enSubtitle.ifPresent(metadata -> subtitlesMap.put(VideoManager.EN, metadata));
        plSubtitle.ifPresent(metadata -> subtitlesMap.put(VideoManager.PL, metadata));
        esSubtitle.ifPresent(metadata -> subtitlesMap.put(VideoManager.ES, metadata));
        return subtitlesMap;
    }

    private void loadMainPosterSrc(MetadataByPathAndType productionFolders, String mainFolderPath, ProductionData productionData) {
        Map<ProductionFileType, List<Metadata>> mainFolderFiles = productionFolders.get(mainFolderPath);
        if (mainFolderFiles == null) return;
        List<Metadata> mainFolderPoster = mainFolderFiles.get(ProductionFileType.POSTER);
        if (mainFolderPoster != null && !mainFolderPoster.isEmpty()) {
            try {
                Metadata posterMeta = mainFolderPoster.get(0);
                byte[] file = fileServiceManager.readFile(posterMeta);
                if (file != null && file.length > 0) {
                    String filename = posterMeta.getFilename();
                    String mime = (filename != null && (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")))
                            ? "image/jpeg" : "image/png";
                    productionData.setBase64PosterSrc("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(file));
                }
            } catch (Exception e) {
                log.error("Error converting poster to base64", e);
            }
        }
    }

    private Map<Metadata, MetadataByPathAndType> loadAllProductionsMetadata() {
        Map<Metadata, MetadataByPathAndType> allVideos = new HashMap<>();
        List<Metadata> allVideosFolders = videoManager.loadVideosMainDirectories();
        log.info("Found {} videos folders", allVideosFolders.size());
        for (Metadata mainVideoFolder : allVideosFolders) {
            log.info("Processing main video folder {}", mainVideoFolder.getPath() + mainVideoFolder.getFilename());
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
