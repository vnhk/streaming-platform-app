package com.bervan.streamingapp;

import com.bervan.common.search.SearchQueryOption;
import com.bervan.common.search.SearchRequest;
import com.bervan.common.search.SearchService;
import com.bervan.common.search.model.SearchOperation;
import com.bervan.common.search.model.SearchResponse;
import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class VideoManager {
    private static final List<String> subtitleSeparator = List.of(".", "_", "-", " ");
    private static final List<String> polSubtitles = List.of("pl", "pol", "polish");
    private static final List<String> engSubtitles = List.of("en", "eng", "english");
    private static final Map<String, List<String>> subtitlesParts = new HashMap<>();

    public static String PL = "pl";
    public static String EN = "en";

    static {
        subtitlesParts.put(PL, polSubtitles);
        subtitlesParts.put(EN, engSubtitles);
    }

    private final WatchDetailsRepository watchDetailsRepository;
    private final List<String> supportedExtensions = Arrays.asList("mp4");
    private final SearchService searchService;
    private final FileServiceManager fileServiceManager;
    @Value("${file.service.storage.folder}")
    public String pathToFileStorage;
    @Value("${streaming-platform.file-storage-relative-path}")
    public String appFolder;


    public VideoManager(WatchDetailsRepository watchDetailsRepository, SearchService searchService, FileServiceManager fileServiceManager) {
        this.watchDetailsRepository = watchDetailsRepository;
        this.searchService = searchService;
        this.fileServiceManager = fileServiceManager;
    }

    private static void putIf(String key, Map<String, List<Metadata>> result, Metadata file) {
        if (!result.containsKey(key)) {
            result.put(key, new ArrayList<>());
        }
        result.get(key).add(file);
    }

    public Optional<Metadata> getSubtitle(String language, List<Metadata> subtitles) {
        List<Metadata> subtitlesFound = new ArrayList<>();

        if (subtitlesParts.get(language).isEmpty()) {
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
                SearchOperation.EQUALS_OPERATION, appFolder.substring(1));
        searchRequest.addCriterion("G1", Metadata.class, "isDirectory",
                SearchOperation.EQUALS_OPERATION, true);

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        options.setSortField("filename");
        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        return response.getResultList();
    }

    public List<Metadata> loadVideos() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addCriterion("G1", Metadata.class, "path",
                SearchOperation.LIKE_OPERATION, appFolder.substring(1) + "%"); //startsWith
        searchRequest.addCriterion("G1", Metadata.class, "extension",
                SearchOperation.IN_OPERATION, supportedExtensions);

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
        return pathToFileStorage + File.separator +
                metadata.getPath() + File.separator + metadata.getFilename();
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

    public Map<String, List<Metadata>> loadVideoDirectoryContent(Metadata directory) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.addCriterion("G1", Metadata.class, "path",
                SearchOperation.EQUALS_OPERATION, directory.getPath() + File.separator + directory.getFilename());

        SearchQueryOption options = new SearchQueryOption(Metadata.class);
        options.setSortField("filename");

        SearchResponse<Metadata> response = searchService.search(searchRequest, options);
        List<Metadata> files = response.getResultList();

        Map<String, List<Metadata>> result = new HashMap<>();
        for (Metadata file : files) {
            if (file.getFilename().equals("poster.png") || file.getFilename().equals("poster.jpg")) {
                putIf("POSTER", result, file);
            } else if (file.getFilename().equals("properties.json")) {
                putIf("PROPERTIES", result, file);
            } else if (file.getFilename().endsWith("vtt") || file.getFilename().endsWith("srt")) {
                putIf("SUBTITLES", result, file);
            } else if (supportedExtensions.contains(file.getExtension())) {
                putIf("VIDEO", result, file);
            } else if (file.isDirectory()) {
                putIf("DIRECTORY", result, file);
            }
        }
        return result;
    }

    public Metadata getMainMovieFolder(Metadata video) {
        Path path = Path.of(video.getPath());
        while (path.getParent() != null) {
            if (appFolder.endsWith(path.getParent().toString())) {
                Path fileName = path.getFileName();
                SearchRequest searchRequest = new SearchRequest();
                searchRequest.addCriterion("G1", Metadata.class, "path",
                        SearchOperation.EQUALS_OPERATION, path.getParent().toString());

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
        while (path.getParent() != null) {
            Path fileName = path.getFileName();
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.addCriterion("G1", Metadata.class, "path",
                    SearchOperation.EQUALS_OPERATION, path.getParent().toString());

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
        vttContent = vttContent.replaceAll("(?m)^\\d+\\s*\\n", "");

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
                List<Metadata> video = loadVideoDirectoryContent(response.getResultList().get(0)).get("VIDEO");
                if (video.size() == 1) {
                    return Optional.ofNullable(video.get(0));
                }
            } else {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

}
