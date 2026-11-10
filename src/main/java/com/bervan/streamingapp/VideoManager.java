package com.bervan.streamingapp;

import com.bervan.common.search.SearchQueryOption;
import com.bervan.common.search.SearchRequest;
import com.bervan.common.search.SearchService;
import com.bervan.common.search.model.SearchOperation;
import com.bervan.common.search.model.SearchResponse;
import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.config.MetadataByPathAndType;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.structure.*;
import com.bervan.streamingapp.config.structure.mp4.MP4EpisodeStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4MovieRootProductionStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4SeasonStructure;
import com.bervan.streamingapp.config.structure.mp4.MP4TvSeriesRootProductionStructure;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class VideoManager {
    private static final List<String> subtitleSeparator = List.of(".", "_", "-", " ");
    private static final List<String> polSubtitles = List.of("pl", "pol", "polish");
    private static final List<String> engSubtitles = List.of("en", "eng", "english");
    private static final List<String> espSubtitles = List.of("es", "esp", "spanish");
    private static final Map<String, List<String>> subtitlesParts = new HashMap<>();
    public static String PL = "pl";
    public static String EN = "en";
    public static String ES = "es";

    static {
        subtitlesParts.put(PL, polSubtitles);
        subtitlesParts.put(EN, engSubtitles);
        subtitlesParts.put(ES, espSubtitles);
    }

    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final WatchDetailsRepository watchDetailsRepository;
    private final List<String> supportedExtensions = Arrays.asList("mp4");
    private final SearchService searchService;
    private final FileServiceManager fileServiceManager;
    @Value("${file.service.storage.folder.main}")
    public String pathToFileStorage;
    @Value("${streaming-platform.file-storage-relative-path:/Streaming Platform}")
    public String appFolder;

    public VideoManager(WatchDetailsRepository watchDetailsRepository, SearchService searchService, FileServiceManager fileServiceManager) {
        this.watchDetailsRepository = watchDetailsRepository;
        this.searchService = searchService;
        this.fileServiceManager = fileServiceManager;
    }

    private static void putIf(ProductionFileType key, Map<ProductionFileType, List<Metadata>> result, Metadata file) {
        if (!result.containsKey(key)) {
            result.put(key, new ArrayList<>());
        }
        result.get(key).add(file);
    }

    @PostConstruct
    public void init() {
        try {
            String folderName = appFolder != null && !appFolder.isBlank() && !appFolder.startsWith("${")
                    ? appFolder.trim() : "Streaming Platform";
            if (folderName.startsWith("/")) folderName = folderName.substring(1);
            while (folderName.endsWith("/")) folderName = folderName.substring(0, folderName.length() - 1);

            final String targetName = folderName;
            boolean existsInDb = fileServiceManager.loadByPath("/").stream()
                    .anyMatch(m -> m.isDirectory() && targetName.equalsIgnoreCase(m.getFilename()));

            if (!existsInDb) {
                fileServiceManager.createEmptyDirectory("/", targetName);
                log.info("Initialized root Streaming Platform directory in DB and storage: {}", targetName);
            }
        } catch (Exception e) {
            log.warn("Could not ensure streaming platform root directory: {}", e.getMessage());
        }
    }

    public Set<String> availableSubtitles(Metadata videoFolder) {
        List<Metadata> subtitles = loadVideoDirectoryContent(videoFolder).get(videoFolder.getPath() + videoFolder.getFilename() + File.separator).get(ProductionFileType.SUBTITLE);
        Set<String> availableSubtitles = new HashSet<>();
        for (String key : subtitlesParts.keySet()) {
            getSubtitle(key, subtitles).ifPresent(e -> availableSubtitles.add(key));
        }

        return availableSubtitles;
    }

    public Optional<Metadata> getSubtitle(String language, List<Metadata> subtitles) {
        List<Metadata> subtitlesFound = new ArrayList<>();

        if (subtitles == null || subtitlesParts.get(language) != null && subtitlesParts.get(language).isEmpty()) {
            return Optional.empty();
        }

        List<String> subtitlesPartsStr = subtitlesParts.get(language);
        for (String engSubtitle : subtitlesPartsStr) {
            for (String separator1 : subtitleSeparator) { //.en_ -en. -en_ etc
                for (String separator2 : subtitleSeparator) {
                    String engPart = separator1 + engSubtitle + separator2;
                    subtitles.stream().filter(e -> e.getFilename().toLowerCase().contains(engPart.toLowerCase()))
                            .findFirst().ifPresent(subtitlesFound::add);
                }
            }
        }

        if (subtitlesFound.isEmpty()) {
            log.info("Found 0 subtitles for language = {}. All available subtitles: {}", language, subtitles.stream()
                    .map(Metadata::getFilename)
                    .collect(Collectors.joining(", ")));
            return Optional.empty();
        }

        if (subtitlesFound.size() > 1) {
            log.warn("Found more than 1 subtitles for language = {}: {}", language, subtitlesFound.stream()
                    .map(Metadata::getFilename)
                    .collect(Collectors.joining(", ")));
        }

        return Optional.of(subtitlesFound.get(0));
    }

    public List<Metadata> loadVideosMainDirectories() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addCriterion("G1", Metadata.class, "path",
                SearchOperation.EQUALS_OPERATION, appFolder + File.separator);
        searchRequest.addCriterion("G1", Metadata.class, "isDirectory",
                SearchOperation.EQUALS_OPERATION, true);

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        options.setSortField("filename");
        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        return response.getResultList();
    }

    public Optional<Metadata> loadVideoMainDirectory(String folderName) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addCriterion("G1", Metadata.class, "path",
                SearchOperation.EQUALS_OPERATION, appFolder + File.separator);
        searchRequest.addCriterion("G1", Metadata.class, "isDirectory",
                SearchOperation.EQUALS_OPERATION, true);
        searchRequest.addCriterion("G1", Metadata.class, "filename",
                SearchOperation.EQUALS_OPERATION, folderName);

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        List<Metadata> resultList = response.getResultList();
        if (resultList.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resultList.get(0));
    }

    public List<Metadata> loadById(String metadataId) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addIdEqualsCriteria("G1", Metadata.class, UUID.fromString(metadataId));

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        options.setSortField("filename");

        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        return response.getResultList();
    }

    @Cacheable(value = "120minCache",
            key = "'hlsDirectory:' + #videoFolderId",
            sync = true)
    public Path getBaseDir(String videoFolderId) {
        List<Metadata> metadata = loadById(videoFolderId);
        if (metadata.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return Path.of(getSrc(metadata.get(0)))
                .toAbsolutePath()
                .normalize();
    }

    public String getSrc(Metadata metadata) {
        return fileServiceManager.getFile(metadata).toString();
    }

    public MetadataByPathAndType loadVideoDirectoryContent(Metadata directory) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addCriterion("G1", Metadata.class, "path",
                SearchOperation.LIKE_OPERATION, directory.getPath() + directory.getFilename() + File.separator + "%");

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        options.setSortField("filename");
        options.setPageSize(100000000);

        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        List<Metadata> files = response.getResultList();

        Map<ProductionFileType, List<Metadata>> result = new HashMap<>();
        for (Metadata file : files) {
            if (file.getFilename().equals("poster.png") || file.getFilename().equals("poster.jpg")) {
                putIf(ProductionFileType.POSTER, result, file);
            } else if (file.getFilename().equals("details.json")) {
                putIf(ProductionFileType.DETAILS, result, file);
            } else if (file.getFilename().endsWith("vtt") || file.getFilename().endsWith("srt")) {
                putIf(ProductionFileType.SUBTITLE, result, file);
            } else if (supportedExtensions.contains(file.getExtension())) {
                putIf(ProductionFileType.VIDEO, result, file);
            } else if (file.isDirectory()) {
                putIf(ProductionFileType.DIRECTORY, result, file);
            }
        }

        MetadataByPathAndType metadataByPath = new MetadataByPathAndType();

        for (Map.Entry<ProductionFileType, List<Metadata>> metadataByType : result.entrySet()) {
            ProductionFileType type = metadataByType.getKey();
            for (Metadata metadata : metadataByType.getValue()) {
                String path = metadata.getPath();
                metadataByPath.computeIfAbsent(path, k -> new HashMap<>());
                Map<ProductionFileType, List<Metadata>> typeMap = metadataByPath.get(path);
                if (typeMap == null) {
                    typeMap = new HashMap<>();
                    typeMap.put(type, new ArrayList<>());
                }

                typeMap.computeIfAbsent(type, k -> new ArrayList<>());
                typeMap.get(type).add(metadata);
            }
        }

        return metadataByPath;
    }

    public String convertSrtToVtt(Metadata subtitle) throws IOException {
        Path inputPath = Paths.get(getSrc(subtitle));

        String content;
        try {
            content = Files.readString(inputPath);
        } catch (Exception e) {
            try {
                content = Files.readString(inputPath, StandardCharsets.UTF_8);
            } catch (Exception e1) {
                content = Files.readString(inputPath, StandardCharsets.ISO_8859_1);
            }
        }
        String vttContent = "WEBVTT\n\n" + content.replace(",", ".");
        vttContent = vttContent.replaceAll("(?m)^\\d+\\s*\\n", "").replaceAll("(?s)<.*?>", "");

        return vttContent;
    }

    public WatchDetails getOrCreateWatchDetails(String userId, String videoId) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addCriterion("G1", WatchDetails.class, "userId",
                SearchOperation.EQUALS_OPERATION, userId);
        searchRequest.addCriterion("G1", WatchDetails.class, "videoId",
                SearchOperation.EQUALS_OPERATION, videoId);

        SearchQueryOption options = new SearchQueryOption(WatchDetails.class);

        SearchResponse<WatchDetails> res = searchService.search(searchRequest, options);
        WatchDetails watchDetails;
        if (res.getResultList().size() != 0) {
            watchDetails = res.getResultList().get(0);
        } else {
            watchDetails = new WatchDetails();
            watchDetails.setVideoId(UUID.fromString(videoId));
            watchDetails.setUserId(UUID.fromString(userId));
            watchDetails.setCurrentVideoTime(0);
            return watchDetailsRepository.save(watchDetails);
        }
        return watchDetails;
    }

    public void saveWatchProgress(WatchDetails watchDetails, double lastWatchedTime) {
        watchDetails.setCurrentVideoTime(lastWatchedTime);
        watchDetailsRepository.save(watchDetails);
    }

    public Optional<Metadata> findSubtitle(Metadata videoFolder, String language) {
        try {
            String folderPath = videoFolder.getPath() + videoFolder.getFilename() + File.separator;
            MetadataByPathAndType contents = loadVideoDirectoryContent(videoFolder);
            Map<ProductionFileType, List<Metadata>> typeMap = contents.get(folderPath);
            if (typeMap == null) return Optional.empty();
            List<Metadata> subtitleList = typeMap.get(ProductionFileType.SUBTITLE);
            if (subtitleList == null) return Optional.empty();
            return getSubtitle(language, subtitleList);
        } catch (Exception e) {
            log.error("Could not find subtitle for language={}", language, e);
            return Optional.empty();
        }
    }

    public Optional<Metadata> findMp4PosterByFolderId(String folderId, Map<String, ProductionData> streamingProductionData) {
        Collection<ProductionData> productions = streamingProductionData.values();
        for (ProductionData productionData : productions) {
            BaseRootProductionStructure productionStructure = productionData.getProductionStructure();
            if (productionStructure instanceof MP4MovieRootProductionStructure) {
                //for movies we get main poster
                if (productionStructure.getMetadataId().toString().equals(folderId)) {
                    return Optional.ofNullable(productionStructure.getPoster());
                }
            } else if (productionStructure instanceof MP4TvSeriesRootProductionStructure) {
                //for tv series we get episode poster
                List<MP4SeasonStructure> seasons = ((MP4TvSeriesRootProductionStructure) productionStructure).getSeasons();
                for (MP4SeasonStructure season : seasons) {
                    for (MP4EpisodeStructure episode : season.getEpisodes()) {
                        if (episode.getMetadataId().toString().equals(folderId)) {
                            return Optional.ofNullable(episode.getPoster());
                        }
                    }
                }
                //if not present then main poster
                if (productionStructure.getMetadataId().toString().equals(folderId)) {
                    return Optional.ofNullable(productionStructure.getPoster());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the next episode folder, including cross-season navigation.
     * If current episode is the last in a season, returns the first episode of the next season.
     */
    public Optional<Metadata> getNextVideoWithCrossSeasonSupport(String currentVideoFolderId, ProductionData productionData) {
        BaseRootProductionStructure productionStructure = productionData.getProductionStructure();

        if (!(productionStructure instanceof TvSeriesBaseRootProductionStructure)) {
            return Optional.empty();
        }

        TvSeriesBaseRootProductionStructure tvSeries = (TvSeriesBaseRootProductionStructure) productionStructure;
        List<? extends SeasonStructure> seasons = tvSeries.getSeasons();

        for (int seasonIndex = 0; seasonIndex < seasons.size(); seasonIndex++) {
            SeasonStructure season = seasons.get(seasonIndex);
            List<? extends EpisodeStructure> episodes = getSortedEpisodes(season);

            for (int episodeIndex = 0; episodeIndex < episodes.size(); episodeIndex++) {
                EpisodeStructure episode = episodes.get(episodeIndex);
                if (episode.getEpisodeFolder().getId().toString().equals(currentVideoFolderId)) {
                    // Found current episode, now get next
                    if (episodeIndex + 1 < episodes.size()) {
                        // Next episode in same season
                        return Optional.of(episodes.get(episodeIndex + 1).getEpisodeFolder());
                    } else if (seasonIndex + 1 < seasons.size()) {
                        // First episode of next season
                        List<? extends EpisodeStructure> nextSeasonEpisodes = getSortedEpisodes(seasons.get(seasonIndex + 1));
                        if (!nextSeasonEpisodes.isEmpty()) {
                            return Optional.of(nextSeasonEpisodes.get(0).getEpisodeFolder());
                        }
                    }
                    // No next episode available
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the previous episode folder, including cross-season navigation.
     * If current episode is the first in a season, returns the last episode of the previous season.
     */
    public Optional<Metadata> getPrevVideoWithCrossSeasonSupport(String currentVideoFolderId, ProductionData productionData) {
        BaseRootProductionStructure productionStructure = productionData.getProductionStructure();

        if (!(productionStructure instanceof TvSeriesBaseRootProductionStructure)) {
            return Optional.empty();
        }

        TvSeriesBaseRootProductionStructure tvSeries = (TvSeriesBaseRootProductionStructure) productionStructure;
        List<? extends SeasonStructure> seasons = tvSeries.getSeasons();

        for (int seasonIndex = 0; seasonIndex < seasons.size(); seasonIndex++) {
            SeasonStructure season = seasons.get(seasonIndex);
            List<? extends EpisodeStructure> episodes = getSortedEpisodes(season);

            for (int episodeIndex = 0; episodeIndex < episodes.size(); episodeIndex++) {
                EpisodeStructure episode = episodes.get(episodeIndex);
                if (episode.getEpisodeFolder().getId().toString().equals(currentVideoFolderId)) {
                    // Found current episode, now get previous
                    if (episodeIndex > 0) {
                        // Previous episode in same season
                        return Optional.of(episodes.get(episodeIndex - 1).getEpisodeFolder());
                    } else if (seasonIndex > 0) {
                        // Last episode of previous season
                        List<? extends EpisodeStructure> prevSeasonEpisodes = getSortedEpisodes(seasons.get(seasonIndex - 1));
                        if (!prevSeasonEpisodes.isEmpty()) {
                            return Optional.of(prevSeasonEpisodes.get(prevSeasonEpisodes.size() - 1).getEpisodeFolder());
                        }
                    }
                    // No previous episode available
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private List<EpisodeStructure> getSortedEpisodes(SeasonStructure season) {
        List<EpisodeStructure> sortedEpisodes = new ArrayList<>();
        List<? extends EpisodeStructure> episodes = season.getEpisodes();
        int maxEpisodes = episodes.size();

        for (int i = 1; i <= maxEpisodes; i++) {
            // Matches: Episode1, Ep1, Ep 1  OR  S1E1, S01E01, S1E10 etc.
            String pattern = "(?:(?:Ep(?:isode)?\\s?)|(?:S\\d+E0*))" + i + "(?![0-9a-zA-Z])";

            Pattern regex = Pattern.compile(pattern);
            for (EpisodeStructure episode : episodes) {
                Matcher matcher = regex.matcher(episode.getMetadataName());
                if (matcher.find() && !sortedEpisodes.contains(episode)) {
                    sortedEpisodes.add(episode);
                    break;
                }
            }
        }

        // Add any remaining episodes that didn't match the pattern
        for (EpisodeStructure episode : episodes) {
            if (!sortedEpisodes.contains(episode)) {
                sortedEpisodes.add(episode);
            }
        }

        return sortedEpisodes;
    }
}
