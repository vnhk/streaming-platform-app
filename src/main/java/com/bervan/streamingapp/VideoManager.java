package com.bervan.streamingapp;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

    private final List<String> supportedExtensions = Arrays.asList("mp4");

    private final FileServiceManager fileServiceManager;

    public VideoManager(FileServiceManager fileServiceManager) {
        this.fileServiceManager = fileServiceManager;
    }

    public List<Metadata> loadVideosMainDirectories() {
        return fileServiceManager.loadVideosByPathStartsWith(appFolder.substring(1))
                .stream().filter(Metadata::isDirectory)
                .filter(e -> e.getPath().equals(appFolder.substring(1)))
                .toList(); //replace first / or \
    }

    public List<Metadata> loadVideos() {
        return fileServiceManager.loadVideosByPathStartsWith(appFolder.substring(1)).stream()
                .filter(e -> supportedExtensions.contains(e.getExtension())).toList();
    }

    public List<Metadata> loadById(String videoId) {
        return fileServiceManager.loadVideoById(videoId)
                .stream()
                .filter(e -> (File.separator + e.getPath()).startsWith(appFolder))
                .toList();
    }

    public String getSrc(Metadata metadata) {
        return pathToFileStorage + File.separator +
                metadata.getPath() + File.separator + metadata.getFilename();
    }

    public List<String> getSupportedExtensions() {
        return new ArrayList<>(supportedExtensions);
    }

    public Map<String, List<Metadata>> loadVideoDirectoryContent(Metadata directory) {
        Map<String, List<Metadata>> result = new HashMap<>();
        Set<Metadata> files = fileServiceManager.loadByPath(directory.getPath() + File.separator + directory.getFilename());
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
                return fileServiceManager.loadVideosByPathStartsWith(path.getParent().toString())
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
            return fileServiceManager.loadVideosByPathStartsWith(path.getParent().toString())
                    .stream().filter(e -> e.getFilename().equals(fileName.toString())).toList().get(0);

        }

        return null;
    }

    public String convertSrtToVtt(Metadata subtitle) throws IOException {
        Path inputPath = Paths.get(getSrc(subtitle));
        String content = Files.readString(inputPath);

        String vttContent = "WEBVTT\n\n" + content.replace(",", ".");
        vttContent = vttContent.replaceAll("\\d+\\n", "");

        return vttContent;
    }
}
