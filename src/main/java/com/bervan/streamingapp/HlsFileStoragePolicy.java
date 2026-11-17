package com.bervan.streamingapp;

import com.bervan.filestorage.model.Metadata;
import com.bervan.filestorage.policy.FileStoragePolicy;
import com.bervan.filestorage.service.FileDiskStorageService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Component
public class HlsFileStoragePolicy implements FileStoragePolicy {
    private static final List<String> SEGMENT_EXTENSIONS = List.of("ts", "m4s");
    private final Path streamingRoot;
    private final FileDiskStorageService disk;

    public HlsFileStoragePolicy(
            @Value("${streaming-platform.file-storage-relative-path:/Streaming Platform}") String streamingRoot,
            FileDiskStorageService disk) {
        this.streamingRoot = Path.of("/" + streamingRoot).normalize();
        this.disk = disk;
    }

    @Override
    public boolean shouldTrackMetadata(Metadata file) {
        return trackingFilter().test(file);
    }

    @Override
    public Predicate<Metadata> trackingFilter(Collection<Metadata> files) {
        Set<Path> playlistDirectories = new HashSet<>();
        for (Metadata file : files) {
            if (!file.isDirectory() && extension(file).equals("m3u8")) {
                playlistDirectories.add(Path.of(file.getPath()).normalize());
            }
        }
        Map<Path, Boolean> directoryCache = new HashMap<>();
        return file -> {
            if (file.isDirectory() || !SEGMENT_EXTENSIONS.contains(extension(file))) return true;
            Path directory = Path.of(file.getPath()).normalize();
            while (directory.startsWith(streamingRoot) && !directory.equals(streamingRoot)) {
                if (playlistDirectories.contains(directory)
                        || directoryCache.computeIfAbsent(directory, this::containsPlaylist)) return false;
                directory = directory.getParent();
            }
            return true;
        };
    }

    @Override
    public List<String> untrackedExtensions(String directory) {
        return Path.of(directory).normalize().startsWith(streamingRoot) ? SEGMENT_EXTENSIONS : List.of();
    }

    private String extension(Metadata file) {
        return FilenameUtils.getExtension(file.getFilename()).toLowerCase(Locale.ROOT);
    }

    private boolean containsPlaylist(Path directory) {
        Path physicalDirectory = disk.getFile(directory.toString());
        if (!Files.exists(physicalDirectory)) return false;
        try (var children = Files.list(physicalDirectory)) {
            return children.anyMatch(file -> Files.isRegularFile(file)
                    && FilenameUtils.getExtension(file.toString()).equalsIgnoreCase("m3u8"));
        } catch (IOException e) {
            // Abort instead of changing tracking decisions when storage cannot be read.
            throw new UncheckedIOException("Cannot inspect playlists in " + directory, e);
        }
    }
}
