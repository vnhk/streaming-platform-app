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
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    @Value("${streaming-platform.file-storage-relative-path}")
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
            log.error("Found 0 subtitles for language = {}. All available subtitles: {}", language, subtitles.stream()
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

    public List<Metadata> loadById(String metadataId) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addIdEqualsCriteria("G1", Metadata.class, UUID.fromString(metadataId));

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        options.setSortField("filename");

        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        return response.getResultList();
    }

    public String getSrc(Metadata metadata) {
        return fileServiceManager.getFile(metadata).toString();
    }

    public Optional<Metadata> getNextVideo(Metadata videoMetadata) {
        Optional<Metadata> videoParentFolder = fileServiceManager.getParent(videoMetadata);

        if (videoParentFolder.isEmpty()) {
            return Optional.empty();
        }
        videoMetadata = videoParentFolder.get();
        String filename = videoMetadata.getFilename();

        String pattern = "(?:Ep(?:isode)?\\s?)(\\d+)";
        Pattern regex = Pattern.compile(pattern);

        int episodeNumber;
        Matcher matcher = regex.matcher(filename);
        if (matcher.find()) {
            episodeNumber = Integer.parseInt(matcher.group(1)) + 1;
        } else {
            return Optional.empty();
        }

        return loadEpisodeVideo(videoMetadata, episodeNumber);
    }

    public Optional<Metadata> getPrevVideo(Metadata videoMetadata) {
        Optional<Metadata> videoParentFolder = fileServiceManager.getParent(videoMetadata);

        if (videoParentFolder.isEmpty()) {
            return Optional.empty();
        }
        videoMetadata = videoParentFolder.get();
        String filename = videoMetadata.getFilename();

        String pattern = "(?:Ep(?:isode)?\\s?)(\\d+)";
        Pattern regex = Pattern.compile(pattern);

        int episodeNumber;
        Matcher matcher = regex.matcher(filename);
        if (matcher.find()) {
            episodeNumber = Integer.parseInt(matcher.group(1)) - 1;
        } else {
            return Optional.empty();
        }

        return loadEpisodeVideo(videoMetadata, episodeNumber);
    }

    public List<String> getSupportedExtensions() {
        return new ArrayList<>(supportedExtensions);
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

    public Metadata getMainMovieFolder(Metadata video) {
        Path path = Path.of(video.getPath());
        while (path.getParent() != null) {
            if (appFolder.endsWith(path.getParent().toString())) {
                Path fileName = path.getFileName();
                SearchRequest searchRequest = new SearchRequest();
                searchRequest.addCriterion("G1", Metadata.class, "path",
                        SearchOperation.EQUALS_OPERATION, path.getParent().toString() + File.separator);

                SearchQueryOption options = new SearchQueryOption(Metadata.class);
                options.setSortField("filename");

                SearchResponse<Metadata> response = searchService.search(searchRequest, options);

                return response.getResultList()
                        .stream().filter(e -> e.getFilename().equals(fileName.toString())).toList().get(0);
            }

            path = path.getParent();
        }

        return null;
    }

    public Metadata getVideoFolder(Metadata video) {
        Path path = Path.of(video.getPath());
        if (path.getParent() != null) {
            Path fileName = path.getFileName();
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.addCriterion("G1", Metadata.class, "path",
                    SearchOperation.EQUALS_OPERATION, path.getParent().toString() + File.separator);

            SearchQueryOption options = new SearchQueryOption(Metadata.class);
            options.setSortField("filename");

            SearchResponse<Metadata> response = searchService.search(searchRequest, options);

            return response.getResultList()
                    .stream().filter(e -> e.getFilename().equals(fileName.toString())).toList().get(0);

        }

        return null;
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

    public void saveSubtitleDelays(WatchDetails watchDetails, double enDelay, double plDelay) {
        watchDetails.setSubtitleDelayEN(enDelay);
        watchDetails.setSubtitleDelayPL(plDelay);

        watchDetailsRepository.save(watchDetails);
    }

    @NotNull
    private Optional<Metadata> loadEpisodeVideo(Metadata metadata, int episodeNumber) {
        Optional<Metadata> parent = fileServiceManager.getParent(metadata);
        if (parent.isPresent()) {
            Metadata parentFolder = parent.get();
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.addCriterion("G1", Metadata.class, "path",
                    SearchOperation.EQUALS_OPERATION, parentFolder.getPath() + File.separator + parentFolder.getFilename());
            searchRequest.addCriterion("G1", Metadata.class, "filename",
                    SearchOperation.IN_OPERATION, List.of("Ep" + episodeNumber, "Ep " + episodeNumber, "Episode" + episodeNumber, "Episode " + episodeNumber));
            searchRequest.addCriterion("G1", Metadata.class, "isDirectory",
                    SearchOperation.EQUALS_OPERATION, true);

            SearchQueryOption options = new SearchQueryOption(Metadata.class);
            options.setSortField("filename");

            SearchResponse<Metadata> response = searchService.search(searchRequest, options);
            if (response.getAllFound() == 1) {
                Metadata directory = response.getResultList().get(0);
                List<Metadata> video = loadVideoDirectoryContent(directory).get(directory.getPath() + directory.getFilename() + File.separator).get("VIDEO");
                if (video.size() == 1) {
                    return Optional.ofNullable(video.get(0));
                }
            } else {
                return Optional.empty();
            }
        }

        return Optional.empty();
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

//    public Map<String, Metadata> findMp4SubtitlesByVideoId(String videoId, Map<String, ProductionData> streamingProductionData) {
//        Collection<ProductionData> productions = streamingProductionData.values();
//        for (ProductionData productionData : productions) {
//            BaseRootProductionStructure productionStructure = productionData.getProductionStructure();
//            if (productionStructure instanceof MP4MovieRootProductionStructure) {
//                //for movies we get subtitles from the main folder
//                for (Metadata video : ((MP4MovieRootProductionStructure) productionStructure).getVideosFolders()) {
//                    if (videoId.equals(video.getId().toString())) {
//                        return ((MP4MovieRootProductionStructure) productionStructure).getSubtitles();
//                    }
//                }
//            } else if (productionStructure instanceof MP4TvSeriesRootProductionStructure) {
//                //for tv series we go to the episode folder
//                List<MP4SeasonStructure> seasons = ((MP4TvSeriesRootProductionStructure) productionStructure).getSeasons();
//                for (MP4SeasonStructure season : seasons) {
//                    for (MP4EpisodeStructure episode : season.getEpisodes()) {
//                        Metadata video = episode.getVideo();
//                        if (video != null && videoId.equals(video.getId().toString())) {
//                            return episode.getSubtitles();
//                        }
//                    }
//                }
//            }
//        }
//        return new HashMap<>();
//    }

    public Optional<Metadata> findVideoFolderById(String videoId, ProductionData productionData) {
        BaseRootProductionStructure productionStructure = productionData.getProductionStructure();
        if (productionStructure instanceof MovieBaseRootProductionStructure) {
            List<Metadata> videoFolders = ((MovieBaseRootProductionStructure) productionStructure).getVideosFolders();
            Optional<Metadata> videoOpt = videoFolders.stream().filter(video -> video.getId().toString().equals(videoId)).findFirst();
            if (videoOpt.isPresent()) {
                return videoOpt;
            }
        } else if (productionStructure instanceof TvSeriesBaseRootProductionStructure) {
            List<? extends SeasonStructure> seasons = ((TvSeriesBaseRootProductionStructure) productionStructure).getSeasons();
            for (SeasonStructure season : seasons) {
                for (EpisodeStructure episode : season.getEpisodes()) {
                    if (episode.getEpisodeFolder().getId().toString().equals(videoId)) {
                        return Optional.of(episode.getEpisodeFolder());
                    }
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

    /**
     * Checks if there is a next episode available.
     */
    public boolean hasNextEpisode(String currentVideoFolderId, ProductionData productionData) {
        return getNextVideoWithCrossSeasonSupport(currentVideoFolderId, productionData).isPresent();
    }

    /**
     * Checks if there is a previous episode available.
     */
    public boolean hasPrevEpisode(String currentVideoFolderId, ProductionData productionData) {
        return getPrevVideoWithCrossSeasonSupport(currentVideoFolderId, productionData).isPresent();
    }

    private List<EpisodeStructure> getSortedEpisodes(SeasonStructure season) {
        List<EpisodeStructure> sortedEpisodes = new ArrayList<>();
        List<? extends EpisodeStructure> episodes = season.getEpisodes();
        int maxEpisodes = episodes.size();

        for (int i = 1; i <= maxEpisodes; i++) {
            String pattern = "(?:Ep(?:isode)?\\s?)" + i + "(?![0-9a-zA-Z])";
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
