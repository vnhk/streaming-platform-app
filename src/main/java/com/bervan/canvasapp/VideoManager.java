package com.bervan.canvasapp;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.service.FileServiceManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, Metadata> loadVideoDirectory(Metadata directory) {
        Map<String, Metadata> result = new HashMap<>();
        List<Metadata> files = fileServiceManager.loadByPathStartsWith(directory.getPath() + File.separator + directory.getFilename());
        for (Metadata file : files) {
            if (file.getFilename().equals("poster.png") || file.getFilename().equals("poster.jpg")) {
                result.put("POSTER", file);
            } else if (file.getFilename().equals("properties.json")) {
                result.put("PROPERTIES", file);
            } else if (supportedExtensions.contains(file.getExtension())) {
                result.put("VIDEO", file);
            }
        }
        return result;
    }
}
