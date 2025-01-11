package com.bervan.streamingapp;

import com.bervan.common.search.SearchQueryOption;
import com.bervan.common.search.SearchRequest;
import com.bervan.common.search.SearchService;
import com.bervan.common.search.model.SearchOperation;
import com.bervan.common.search.model.SearchResponse;
import com.bervan.filestorage.model.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class VideoManager {
    @Value("${file.service.storage.folder}")
    public String pathToFileStorage;
    @Value("${streaming-platform.file-storage-relative-path}")
    public String appFolder;

    private final WatchDetailsRepository watchDetailsRepository;

    private final List<String> supportedExtensions = Arrays.asList("mp4");

    private final SearchService searchService;

    public VideoManager(WatchDetailsRepository watchDetailsRepository, SearchService searchService) {
        this.watchDetailsRepository = watchDetailsRepository;
        this.searchService = searchService;
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

    private static void putIf(String key, Map<String, List<Metadata>> result, Metadata file) {
        if (!result.containsKey(key)) {
            result.put(key, new ArrayList<>());
        }
        result.get(key).add(file);
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
}
