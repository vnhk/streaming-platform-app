package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
import com.bervan.logging.BaseProcessContext;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.config.MetadataByPathAndType;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.structure.ProductionFileType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.util.InMemoryResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/storage/videos")
public class VideoController {
    private final VideoManager videoManager;
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final Map<String, ProductionData> streamingProductionData;

    public VideoController(VideoManager videoManager, Map<String, ProductionData> streamingProductionData) {
        this.videoManager = videoManager;
        this.streamingProductionData = streamingProductionData;
    }

    @GetMapping("/download-and-convert/{videoFolderId}")
    public WebAsyncTask<ResponseEntity<StreamingResponseBody>> downloadAndConvert(@PathVariable String videoFolderId) {
        BaseProcessContext context = BaseProcessContext.builder()
                .processName("download-and-convert").build();
        // Create WebAsyncTask with 30-minute timeout only for this endpoint
        WebAsyncTask<ResponseEntity<StreamingResponseBody>> webAsyncTask = new WebAsyncTask<>(
                30 * 60 * 1000L, // 30 minutes timeout
                () -> {
                    try {
                        // Security check
                        if (AuthService.getLoggedUserId() == null) {
                            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                        }

                        // Load video metadata
                        List<Metadata> videoFolder = videoManager.loadById(videoFolderId);
                        if (videoFolder.size() != 1) {
                            log.error(context.map(), "Could not find file based on provided id: " + videoFolderId);
                            return ResponseEntity.badRequest().build();
                        }

                        Metadata videoFolderSingle = videoFolder.get(0);
                        Path hlsBaseDir = Path.of(videoManager.getSrc(videoFolderSingle));

                        // Find the main m3u8 file
                        Path mainM3u8 = findMainM3u8File(hlsBaseDir, context);
                        if (mainM3u8 == null || !Files.exists(mainM3u8)) {
                            log.error(context.map(), "Could not find main m3u8 file in directory: " + hlsBaseDir);
                            return ResponseEntity.badRequest().build();
                        }

                        log.info(context.map(), "Starting conversion of HLS to MP4. Input: " + mainM3u8.toString());
                        String outputFilename = videoFolderSingle.getFilename() + ".mp4";

                        StreamingResponseBody stream = outputStream -> {
                            Process process = null;
                            Thread errorReaderThread = null;
                            long startTime = System.currentTimeMillis();

                            try {
                                // Before running FFmpeg, check playlist content
                                try {
                                    String playlistContent = Files.readString(mainM3u8);
                                    log.info("Using playlist content preview: " +
                                            playlistContent.lines().limit(10).collect(Collectors.joining("\n")));

                                    int segmentCount = (int) playlistContent.lines()
                                            .filter(line -> line.contains("#EXTINF"))
                                            .count();
                                    log.info("Playlist contains {} segments", segmentCount);
                                } catch (Exception e) {
                                    log.warn("Could not read playlist for debugging: " + e.getMessage());
                                }

                                ProcessBuilder processBuilder = new ProcessBuilder();
                                processBuilder.command(
                                        "ffmpeg",
                                        "-y",
                                        "-allowed_extensions", "ALL",
                                        "-protocol_whitelist", "file,http,https,tcp,tls,crypto",
                                        "-i", mainM3u8.toString(),
                                        "-c", "copy",
                                        "-bsf:a", "aac_adtstoasc",
                                        "-movflags", "frag_keyframe+empty_moov",
                                        "-f", "mp4",
                                        "-v", "info",
                                        "pipe:1"
                                );

                                processBuilder.directory(hlsBaseDir.toFile());
                                processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

                                process = processBuilder.start();
                                final Process finalProcess = process;

                                errorReaderThread = new Thread(() -> {
                                    try (var errorReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                                        String line;
                                        while ((line = errorReader.readLine()) != null) {
                                            // Log everything from FFmpeg to see what's happening
                                            if (line.contains("frame=") || line.contains("time=")) {
                                                log.info("FFmpeg progress: " + line);
                                            } else if (line.contains("Input") || line.contains("Output") || line.contains("Stream mapping")) {
                                                log.info("FFmpeg info: " + line);
                                            } else if (line.contains("Error") || line.contains("error") || line.contains("Failed")) {
                                                log.error("FFmpeg error: " + line);
                                            } else {
                                                log.debug("FFmpeg: " + line);
                                            }
                                        }
                                    } catch (IOException e) {
                                        log.debug("Error reading FFmpeg stderr: " + e.getMessage());
                                    }
                                });
                                errorReaderThread.setDaemon(true);
                                errorReaderThread.start();

                                try (var processOutput = process.getInputStream()) {
                                    byte[] buffer = new byte[32768]; // Back to larger buffer
                                    int bytesRead;
                                    long totalBytes = 0;
                                    long lastLogTime = System.currentTimeMillis();

                                    while ((bytesRead = processOutput.read(buffer)) != -1) {
                                        try {
                                            outputStream.write(buffer, 0, bytesRead);
                                            totalBytes += bytesRead;

                                            long currentTime = System.currentTimeMillis();
                                            // Flush every second or every 1MB
                                            if ((currentTime - lastLogTime) > 1000 || totalBytes % (1024 * 1024) == 0) {
                                                outputStream.flush();
                                                lastLogTime = currentTime;

                                                // Log every 5MB
                                                if (totalBytes % (5 * 1024 * 1024) == 0) {
                                                    long elapsedSeconds = (currentTime - startTime) / 1000;
                                                    log.info("Streamed {} MB in {} seconds", totalBytes / 1024 / 1024, elapsedSeconds);
                                                }
                                            }

                                        } catch (IOException e) {
                                            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                                            log.info("Client disconnected after {} seconds. Total bytes sent: {} MB",
                                                    elapsedTime, totalBytes / 1024 / 1024);

                                            if (process != null && process.isAlive()) {
                                                process.destroyForcibly();
                                            }
                                            return;
                                        }
                                    }

                                    int exitCode = process.waitFor();
                                    long totalTime = (System.currentTimeMillis() - startTime) / 1000;

                                    log.info("FFmpeg finished with exit code: {}. Total: {} MB in {} seconds",
                                            exitCode, totalBytes / 1024 / 1024, totalTime);

                                } catch (IOException e) {
                                    log.error("Stream error: {}", e.getMessage());
                                    if (process != null && process.isAlive()) {
                                        process.destroyForcibly();
                                    }
                                }

                            } catch (Exception e) {
                                log.error("Conversion error: {}", e.getMessage());
                                if (process != null && process.isAlive()) {
                                    process.destroyForcibly();
                                }
                            }
                        };

                        return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + outputFilename + "\"")
                                .header("Content-Type", "video/mp4")
                                .header("Cache-Control", "no-cache")
                                .body(stream);

                    } catch (Exception e) {
                        log.error(context.map(), "Error in downloadAndConvert", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                }
        );

        // Optional: Add timeout handler
        webAsyncTask.onTimeout(() -> {
            log.warn(context.map(), "Video conversion timed out after 30 minutes");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .build();
        });

        return webAsyncTask;
    }

    private Path findMainM3u8File(Path hlsBaseDir, BaseProcessContext context) {
        try {
            // Look for the main playlist file
            List<Path> m3u8Files = Files.walk(hlsBaseDir)
                    .filter(path -> path.toString().toLowerCase().endsWith(".m3u8"))
                    .collect(Collectors.toList());

            log.info(context.map(), "Found " + m3u8Files.size() + " m3u8 files: " + m3u8Files);

            // Try to find master playlist first
            for (Path m3u8File : m3u8Files) {
                try {
                    String content = Files.readString(m3u8File);
                    if (content.contains("#EXT-X-STREAM-INF")) {
                        log.info(context.map(), "Found master playlist: " + m3u8File);
                        return m3u8File;
                    }
                } catch (IOException e) {
                    log.warn(context.map(), "Could not read m3u8 file: " + m3u8File, e);
                }
            }

            // If no master playlist found, look for media playlist
            for (Path m3u8File : m3u8Files) {
                try {
                    String content = Files.readString(m3u8File);
                    if (content.contains("#EXTINF")) {
                        log.info(context.map(), "Found media playlist: " + m3u8File);
                        return m3u8File;
                    }
                } catch (IOException e) {
                    log.warn(context.map(), "Could not read m3u8 file: " + m3u8File, e);
                }
            }

            // Fallback to first m3u8 file found
            if (!m3u8Files.isEmpty()) {
                log.info(context.map(), "Using first m3u8 file found: " + m3u8Files.get(0));
                return m3u8Files.get(0);
            }

            return null;
        } catch (IOException e) {
            log.error(context.map(), "Error searching for m3u8 file", e);
            return null;
        }
    }

    @GetMapping("/poster/{folderId}")
    public ResponseEntity<Resource> servePoster(@PathVariable String folderId) {
        try {
            List<Metadata> metadataL = videoManager.loadById(folderId);

            if (metadataL.size() != 1) {
                log.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Optional<Metadata> poster = videoManager.findMp4PosterByFolderId(folderId, streamingProductionData);

            if (poster.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            Path file = Path.of(videoManager.getSrc(poster.get()));
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok().contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_PNG)).body(resource);
        } catch (Exception e) {
            log.error("Failed to load poster", e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/poster/direct/{metadataId}")
    public ResponseEntity<Resource> servePosterDirectly(@PathVariable String metadataId) {
        try {
            List<Metadata> metadata = videoManager.loadById(metadataId);

            if (metadata.size() != 1) {
                log.error("Could not find file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Metadata m = metadata.get(0);
            if (!(m.getFilename().endsWith(".jpg") || m.getFilename().endsWith(".png"))) {
                log.error("File is not jpg or png based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Path file = Path.of(videoManager.getSrc(m));
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok().contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_PNG)).body(resource);
        } catch (Exception e) {
            log.error("Failed to load poster", e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/subtitles/{videoFolderId}/{language}")
    public ResponseEntity<Resource> getSubtitles(@PathVariable String videoFolderId, @PathVariable String language) {
        try {
            List<Metadata> videoFolder = videoManager.loadById(videoFolderId);

            if (videoFolder.size() != 1) {
                log.error("Could not find video file based on provided id!");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Metadata videoFolderSingle = videoFolder.get(0);
            MetadataByPathAndType metadataByPathAndType = videoManager.loadVideoDirectoryContent(videoFolderSingle);
            List<Metadata> subtitles = metadataByPathAndType.get(videoFolderSingle.getPath() + videoFolderSingle.getFilename() + File.separator).get(ProductionFileType.SUBTITLE);


            Optional<Metadata> subtitle = videoManager.getSubtitle(language, subtitles);
            if (subtitle.isPresent()) {

                Resource resource = getSubtitleResource(subtitle.get());
                if (resource.exists() && resource.isReadable()) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.valueOf("text/vtt"));

                    return ResponseEntity
                            .ok()
                            .headers(headers)
                            .body(resource);
                } else {
                    return ResponseEntity.notFound().build();
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error! ", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Resource getSubtitleResource(Metadata subtitle) throws IOException {
        if (subtitle.getExtension().equals("vtt")) {
            Path subtitlesPath = Path.of(videoManager.getSrc(subtitle));
            return new UrlResource(subtitlesPath.toUri());
        } else if (subtitle.getExtension().equals("srt")) {
            return new InMemoryResource(videoManager.convertSrtToVtt(subtitle));
        }

        throw new RuntimeException(subtitle.getExtension() + " is not supported for subtitles!");
    }

    @GetMapping("/hls/{videoFolderId}/**")
    public ResponseEntity<Resource> serveHls(HttpServletRequest request,
                                             @PathVariable String videoFolderId) throws Exception {

        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Metadata> metadata = videoManager.loadById(videoFolderId);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path baseDir = Path.of(videoManager.getSrc(metadata.get(0)));

        String fullPath = request.getRequestURI();
        String prefix = "/storage/videos/hls/" + videoFolderId + "/";

        if (!fullPath.startsWith(prefix)) {
            return ResponseEntity.badRequest().build();
        }

        String path = fullPath.substring(prefix.length());

        // Decode URL encoded characters (spaces, etc.)
        path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);

        Path file = baseDir.resolve(path).normalize();

        // Security check
        if (!file.startsWith(baseDir)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(file)) {
            log.error("HLS file not found: " + file);
            return ResponseEntity.notFound().build();
        }

        // Determine content type
        String fileName = file.toString().toLowerCase();
        String contentType;
        if (fileName.endsWith(".m3u8")) {
            contentType = "application/vnd.apple.mpegurl";
        } else if (fileName.endsWith(".ts")) {
            contentType = "video/mp2t";
        } else if (fileName.endsWith(".aac")) {
            contentType = "audio/aac";
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".m4s")) {
            contentType = "video/mp4";
        } else if (fileName.endsWith(".m4a")) {
            contentType = "audio/mp4";
        } else if (fileName.endsWith(".vtt")) {
            contentType = "text/vtt";
        } else {
            contentType = "application/octet-stream";
        }

        // Cache control
        String cacheControl = fileName.endsWith(".m3u8") ? "no-cache" : "max-age=3600";

        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .header("Access-Control-Allow-Origin", "*")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new UrlResource(file.toUri()));
    }

    @GetMapping("/video-folder/{videoFolderId}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable String videoFolderId,
            @RequestHeader(value = "Range", required = false) String httpRangeList
    ) throws IOException {
        // Security check using AuthService
        if (AuthService.getLoggedUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Metadata> videoFolder = videoManager.loadById(videoFolderId);

        if (videoFolder.size() != 1) {
            log.error("Could not find file based on provided id!");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            Metadata videoFolderSingle = videoFolder.get(0);
            MetadataByPathAndType metadataByPathAndType = videoManager.loadVideoDirectoryContent(videoFolderSingle);
            List<Metadata> video = metadataByPathAndType.get(videoFolderSingle.getPath() + videoFolderSingle.getFilename() + File.separator).get(ProductionFileType.VIDEO);
            Path file = Path.of(videoManager.getSrc(video.get(0)));
            FileSystemResource videoResource = new FileSystemResource(file);

            long contentLength = videoResource.contentLength();
            ResourceRegion region;
            long rangeLength = 0;
            long start = 0, end;

            if (httpRangeList == null) {
                region = new ResourceRegion(videoResource, 0, contentLength);
            } else {
                String[] ranges = httpRangeList.replace("bytes=", "").split("-");
                start = Long.parseLong(ranges[0]);
                end = ranges.length > 1 ? Long.parseLong(ranges[1]) : contentLength - 1;
                rangeLength = Math.min(5_000_000, end - start + 1);
                region = new ResourceRegion(videoResource, start, rangeLength);
            }

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaTypeFactory
                            .getMediaType(videoResource)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .header("Accept-Ranges", "bytes")
                    .contentLength(rangeLength)
                    .body(region);
        } catch (Exception e) {
            log.error("Error! ", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private Path findMainM3u8File(Path hlsBaseDir) {
        try {
            List<Path> m3u8Files = Files.walk(hlsBaseDir)
                    .filter(path -> path.toString().toLowerCase().endsWith(".m3u8"))
                    .collect(Collectors.toList());

            log.info("Found " + m3u8Files.size() + " m3u8 files: " + m3u8Files);

            // First check for master playlist
            for (Path m3u8File : m3u8Files) {
                try {
                    String content = Files.readString(m3u8File);
                    if (content.contains("#EXT-X-STREAM-INF")) {
                        log.info("Found master playlist: " + m3u8File);

                        // IMPORTANT: Check if master playlist has correct paths to video playlist
                        String[] lines = content.split("\n");
                        for (String line : lines) {
                            if (!line.startsWith("#") && line.trim().length() > 0) {
                                Path videoPlaylist = m3u8File.getParent().resolve(line.trim());
                                if (Files.exists(videoPlaylist)) {
                                    log.info("Master playlist points to existing video playlist: " + videoPlaylist);
                                    // Return video playlist directly instead of master
                                    return videoPlaylist;
                                } else {
                                    log.warn("Master playlist points to non-existing file: " + videoPlaylist);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    log.warn("Could not read m3u8 file: " + m3u8File, e);
                }
            }

            // If no good master found, find largest video playlist
            Path bestVideoPlaylist = null;
            int maxSegments = 0;

            for (Path m3u8File : m3u8Files) {
                try {
                    String content = Files.readString(m3u8File);
                    if (content.contains("#EXTINF")) {
                        // Count segments
                        int segmentCount = (int) content.lines()
                                .filter(line -> line.contains("#EXTINF"))
                                .count();

                        log.info("Video playlist {} has {} segments", m3u8File, segmentCount);

                        if (segmentCount > maxSegments) {
                            maxSegments = segmentCount;
                            bestVideoPlaylist = m3u8File;
                        }
                    }
                } catch (IOException e) {
                    log.warn("Could not read m3u8 file: " + m3u8File, e);
                }
            }

            if (bestVideoPlaylist != null) {
                log.info("Using video playlist with most segments ({}): {}", maxSegments, bestVideoPlaylist);
                return bestVideoPlaylist;
            }

            // Fallback
            if (!m3u8Files.isEmpty()) {
                log.warn("Using first m3u8 file found as fallback: " + m3u8Files.get(0));
                return m3u8Files.get(0);
            }

            return null;
        } catch (IOException e) {
            log.error("Error searching for m3u8 file", e);
            return null;
        }
    }
}