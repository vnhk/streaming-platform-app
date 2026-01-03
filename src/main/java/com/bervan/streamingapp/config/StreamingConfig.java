package com.bervan.streamingapp.config;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.VideoManager;
import com.bervan.streamingapp.config.structure.BaseRootProductionStructure;
import com.bervan.streamingapp.config.structure.hls.HLSEpisodeStructure;
import com.bervan.streamingapp.config.structure.hls.HLSMovieRootProductionStructure;
import com.bervan.streamingapp.config.structure.hls.HLSSeasonStructure;
import com.bervan.streamingapp.config.structure.hls.HLSTvSeriesRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4EpisodeStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4MovieRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4SeasonStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4TvSeriesRootProductionStructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        Map<Metadata, MetadataByPathAndType> allProductions = loadAllProductionsMetadata();

        for (Map.Entry<Metadata, MetadataByPathAndType> productionEntry : allProductions.entrySet()) {
            ProductionData productionData = new ProductionData();
            Metadata mainFolder = productionEntry.getKey();
            productionData.setMainFolder(mainFolder);
            String mainFolderPath = (mainFolder.getPath() + mainFolder.getFilename() + File.separator).trim();
            log.info("Building production's data :{}", mainFolderPath);
            productionData.setMainFolderPath(mainFolderPath);
            productionData.setProductionId(productionEntry.getKey().getId().toString());
            productionData.setProductionFoldersByPathAndType(productionEntry.getValue());

            MetadataByPathAndType productionFolders = productionEntry.getValue();

            if (productionFolders.get(mainFolderPath) == null) {
                log.error("Production Folders Empty: Details file is missing for production " + mainFolderPath);
                continue;
            }

            List<Metadata> details = productionFolders.get(mainFolderPath).get("DETAILS");
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
                log.error("Details file does not exist or cannot be loaded: Details file is missing for production " + mainFolderPath);
                continue;
            }

            loadMainPosterSrc(productionFolders, mainFolderPath, productionData);
            loadProductionStructure(productionData, productionFolders);

            result.put(productionData.getProductionName(), productionData);
        }

        long end = System.currentTimeMillis();
        log.info("Loading all productions finished in {} ms", end - start);
        log.info("Loaded Productions: [{}]", String.join(", ", result.keySet()));
        return result;
    }

    private void loadProductionStructure(ProductionData productionData, MetadataByPathAndType productionFolders) {
        try {
            if (productionData.getProductionDetails().getVideoFormat() == ProductionDetails.VideoFormat.HLS) {
                loadHLSProductionStructure(productionData, productionFolders);
            } else {
                loadMP4ProductionStructure(productionData, productionFolders);
            }
        } catch (Exception e) {
            log.error("Error loading production structure", e);
        }
    }

    private void loadHLSProductionStructure(ProductionData productionData, MetadataByPathAndType productionFolders) {
        BaseRootProductionStructure rootProductionStructure;
        if (productionData.getProductionDetails().getType() == ProductionDetails.VideoType.TV_SERIES) {
            rootProductionStructure = new HLSTvSeriesRootProductionStructure();
            Map<String, List<Metadata>> seasonsMap = productionFolders.get(productionData.getMainFolderPath());
            List<Metadata> seasonDirectories = seasonsMap.get("DIRECTORY");
            List<HLSSeasonStructure> seasonStructureList = new ArrayList<>();
            if (seasonDirectories != null && !seasonDirectories.isEmpty()) {
                for (Metadata seasonDirectory : seasonDirectories) {
                    HLSSeasonStructure seasonStructure = new HLSSeasonStructure();
                    seasonStructure.setSeasonFolder(seasonDirectory);
                    Map<String, List<Metadata>> seasonsFilesMap = productionFolders.get(seasonDirectory.getPath() + seasonDirectory.getFilename() + File.separator);
                    List<Metadata> episodeDirectories = seasonsFilesMap.get("DIRECTORY");
                    List<HLSEpisodeStructure> episodeStructureList = new ArrayList<>();
                    for (Metadata episodeDirectory : episodeDirectories) {
                        HLSEpisodeStructure episodeStructure = new HLSEpisodeStructure();
                        episodeStructure.setEpisodeFolder(episodeDirectory);
                        Map<String, List<Metadata>> episodeFilesMap = productionFolders.get(episodeDirectory.getPath() + episodeDirectory.getFilename() + File.separator);
                        List<Metadata> poster = episodeFilesMap.get("POSTER");
                        if (poster != null && !poster.isEmpty()) {
                            episodeStructure.setPoster(poster.get(0));
                        }
                        episodeStructureList.add(episodeStructure);
                    }
                    seasonStructure.setEpisodes(episodeStructureList);
                    seasonStructureList.add(seasonStructure);
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
        rootProductionStructure.setDetails(productionFolders.get(productionData.getMainFolderPath()).get("DETAILS").get(0));
        List<Metadata> poster = productionFolders.get(productionData.getMainFolderPath()).get("POSTER");
        if (poster != null && !poster.isEmpty()) {
            rootProductionStructure.setPoster(poster.get(0));
        }
    }

    private void loadMP4ProductionStructure(ProductionData productionData, MetadataByPathAndType productionFolders) {
        BaseRootProductionStructure rootProductionStructure;
        if (productionData.getProductionDetails().getType() == ProductionDetails.VideoType.TV_SERIES) {
            rootProductionStructure = new MP4TvSeriesRootProductionStructure();
            Map<String, List<Metadata>> seasonsMap = productionFolders.get(productionData.getMainFolderPath());
            List<Metadata> seasonDirectories = seasonsMap.get("DIRECTORY");
            List<MP4SeasonStructure> seasonStructureList = new ArrayList<>();
            if (seasonDirectories != null && !seasonDirectories.isEmpty()) {
                for (Metadata seasonDirectory : seasonDirectories) {
                    MP4SeasonStructure seasonStructure = new MP4SeasonStructure();
                    seasonStructure.setSeasonFolder(seasonDirectory);
                    Map<String, List<Metadata>> seasonsFilesMap = productionFolders.get(seasonDirectory.getPath() + seasonDirectory.getFilename() + File.separator);
                    List<Metadata> episodeDirectories = seasonsFilesMap.get("DIRECTORY");
                    List<MP4EpisodeStructure> episodeStructureList = new ArrayList<>();
                    for (Metadata episodeDirectory : episodeDirectories) {
                        MP4EpisodeStructure episodeStructure = new MP4EpisodeStructure();
                        episodeStructure.setEpisodeFolder(episodeDirectory);
                        Map<String, List<Metadata>> episodeFilesMap = productionFolders.get(episodeDirectory.getPath() + episodeDirectory.getFilename() + File.separator);
                        List<Metadata> poster = episodeFilesMap.get("POSTER");
                        if (poster != null && !poster.isEmpty()) {
                            episodeStructure.setPoster(poster.get(0));
                        }

                        List<Metadata> video = episodeFilesMap.get("VIDEO");
                        if (video != null && !video.isEmpty()) {
                            episodeStructure.setVideo(video.get(0));
                        }

                        List<Metadata> subtitles = episodeFilesMap.get("SUBTITLES");
                        episodeStructure.setSubtitles(getSubtitlesMap(subtitles));
                        episodeStructureList.add(episodeStructure);
                    }
                    seasonStructure.setEpisodes(episodeStructureList);
                    seasonStructureList.add(seasonStructure);
                }
            }
            ((MP4TvSeriesRootProductionStructure) rootProductionStructure).setSeasons(seasonStructureList);
        } else {
            rootProductionStructure = new MP4MovieRootProductionStructure();
            ((MP4MovieRootProductionStructure) rootProductionStructure).setVideosFolders(productionFolders.get(productionData.getMainFolderPath()).get("VIDEO"));
        }
        updateRoot(productionData, productionFolders, rootProductionStructure);

        productionData.setProductionStructure(rootProductionStructure);
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
        List<Metadata> mainFolderPoster = productionFolders.get(mainFolderPath).get("POSTER");
        if (mainFolderPoster != null && !mainFolderPoster.isEmpty()) {
            try {
                byte[] file = fileServiceManager.readFile(mainFolderPoster.get(0));
                productionData.setBase64PosterSrc(toBase64(new ByteArrayInputStream(file)));
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
