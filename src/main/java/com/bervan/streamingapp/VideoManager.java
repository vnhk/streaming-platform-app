package com.bervan.streamingapp;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
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

    public List<Metadata> loadVideosDirectories() {
        return fileServiceManager.loadByPathStartsWith(appFolder.substring(1))
                .stream().filter(Metadata::isDirectory).toList(); //replace first / or \
    }

    public List<Metadata> loadById(String videoId) {
        return fileServiceManager.loadById(videoId)
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

    public Map<String, List<Metadata>> loadVideoDirectory(Metadata directory) {
        Map<String, List<Metadata>> result = new HashMap<>();
        List<Metadata> files = fileServiceManager.loadByPathStartsWith(directory.getPath() + File.separator + directory.getFilename());
        for (Metadata file : files) {
            if (file.getFilename().equals("poster.png") || file.getFilename().equals("poster.jpg")) {
                putIf("POSTER", result, file);
            } else if (file.getFilename().equals("properties.json")) {
                putIf("PROPERTIES", result, file);
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
}
