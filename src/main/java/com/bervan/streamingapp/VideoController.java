package com.bervan.streamingapp;

import com.bervan.common.service.AuthService;
import com.bervan.filestorage.model.Metadata;
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
                            log.error("Could not find file based on provided id: " + videoFolderId);
                            return ResponseEntity.badRequest().build();
                        }

                        Metadata videoFolderSingle = videoFolder.get(0);
                        Path hlsBaseDir = Path.of(videoManager.getSrc(videoFolderSingle));

                        // Find the main m3u8 file
                        Path mainM3u8 = findMainM3u8File(hlsBaseDir);
                        if (mainM3u8 == null || !Files.exists(mainM3u8)) {
                            log.error("Could not find main m3u8 file in directory: " + hlsBaseDir);
                            return ResponseEntity.badRequest().build();
                        }

                        log.info("Starting conversion of HLS to MP4. Input: " + mainM3u8.toString());
                        String outputFilename = videoFolderSingle.getFilename() + ".mp4";

                        StreamingResponseBody stream = outputStream -> {
                            Process process = null;
                            Thread errorReaderThread = null;
                            long startTime = System.currentTimeMillis();

                            try {
                                ProcessBuilder processBuilder = new ProcessBuilder();
                                processBuilder.command(
                                        "ffmpeg",
                                        "-y",  // Overwrite output without asking
                                        "-i", mainM3u8.toString(),
                                        "-c:v", "libx264",  // Re-encode video to ensure compatibility
                                        "-c:a", "aac",      // Re-encode audio to AAC
                                        "-preset", "fast",   // Fast encoding preset
                                        "-crf", "23",        // Constant Rate Factor for quality
                                        "-movflags", "frag_keyframe+empty_moov+faststart",  // Enable streaming and fast start
                                        "-f", "mp4",
                                        "-progress", "pipe:2",  // Send progress to stderr
                                        "pipe:1"  // Output to stdout
                                );

                                // Set working directory to the HLS directory
                                processBuilder.directory(hlsBaseDir.toFile());
                                processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

                                process = processBuilder.start();
                                final Process finalProcess = process;

                                // Create a thread to read and log stderr with progress tracking
                                errorReaderThread = new Thread(() -> {
                                    try (var errorReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                                        String line;
                                        long lastProgressTime = System.currentTimeMillis();

                                        while ((line = errorReader.readLine()) != null) {
                                            long currentTime = System.currentTimeMillis();

                                            // Log progress every 30 seconds instead of every line
                                            if (line.contains("frame=") && (currentTime - lastProgressTime) > 30000) {
                                                log.info("FFmpeg progress: " + line);
                                                lastProgressTime = currentTime;
                                            } else if (line.contains("Error") || line.contains("error") || line.contains("Warning")) {
                                                log.warn("FFmpeg: " + line);
                                            }
                                        }
                                    } catch (IOException e) {
                                        log.debug("Error reading FFmpeg stderr (likely process terminated): " + e.getMessage());
                                    }
                                });
                                errorReaderThread.setDaemon(true); // Make it a daemon thread
                                errorReaderThread.start();

                                // Stream the output directly to the response
                                try (var processOutput = process.getInputStream()) {
                                    byte[] buffer = new byte[65536];  // 64KB buffer for better performance
                                    int bytesRead;
                                    long totalBytes = 0;
                                    long lastHeartbeat = System.currentTimeMillis();

                                    while ((bytesRead = processOutput.read(buffer)) != -1) {
                                        try {
                                            outputStream.write(buffer, 0, bytesRead);
                                            totalBytes += bytesRead;

                                            long currentTime = System.currentTimeMillis();

                                            // Flush every 1MB or every 10 seconds
                                            if (totalBytes % (1024 * 1024) == 0 || (currentTime - lastHeartbeat) > 10000) {
                                                outputStream.flush();
                                                lastHeartbeat = currentTime;

                                                // Log progress every 100MB
                                                if (totalBytes % (100 * 1024 * 1024) == 0) {
                                                    long elapsedSeconds = (currentTime - startTime) / 1000;
                                                    log.info("Streamed " + (totalBytes / 1024 / 1024) + " MB in " + elapsedSeconds + " seconds");
                                                }
                                            }

                                        } catch (IOException e) {
                                            // Client disconnected or connection lost
                                            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                                            log.info("Client disconnected after {} seconds. Total bytes sent: {} MB. Terminating FFmpeg process.",
                                                    elapsedTime, totalBytes / 1024 / 1024);
                                            if (process != null && process.isAlive()) {
                                                process.destroyForcibly();
                                            }
                                            return;
                                        }
                                    }

                                    long totalTime = (System.currentTimeMillis() - startTime) / 1000;
                                    log.info("Conversion completed. Total: {} MB in {} seconds",
                                            totalBytes / 1024 / 1024, totalTime);

                                } catch (IOException e) {
                                    log.warn("Stream interrupted after {} seconds: {}",
                                            (System.currentTimeMillis() - startTime) / 1000, e.getMessage());
                                    if (process != null && process.isAlive()) {
                                        process.destroyForcibly();
                                    }
                                    return;
                                }

                                // Wait for process to complete
                                int exitCode = process.waitFor();

                                if (errorReaderThread != null && errorReaderThread.isAlive()) {
                                    errorReaderThread.join(2000);
                                }

                                if (exitCode != 0) {
                                    log.error("FFmpeg process failed with exit code: {} after {} seconds",
                                            exitCode, (System.currentTimeMillis() - startTime) / 1000);
                                    return;
                                }

                                long totalTime = (System.currentTimeMillis() - startTime) / 1000;
                                log.info("FFmpeg conversion completed successfully in {} seconds", totalTime);

                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                                log.info("Conversion interrupted after {} seconds", elapsedTime);
                                if (process != null && process.isAlive()) {
                                    process.destroyForcibly();
                                }
                            } catch (Exception e) {
                                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                                log.error("Error during FFmpeg conversion after {} seconds: {}", elapsedTime, e.getMessage());
                                if (process != null && process.isAlive()) {
                                    process.destroyForcibly();
                                }
                            } finally {
                                // Cleanup
                                if (process != null && process.isAlive()) {
                                    try {
                                        if (!process.destroyForcibly().waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                                            log.warn("FFmpeg process did not terminate within 5 seconds");
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }

                                if (errorReaderThread != null && errorReaderThread.isAlive()) {
                                    errorReaderThread.interrupt();
                                }
                            }
                        };

                        return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + outputFilename + "\"")
                                .header("Content-Type", "video/mp4")
                                .header("Cache-Control", "no-cache")
                                .body(stream);

                    } catch (Exception e) {
                        log.error("Error in downloadAndConvert", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                }
        );

        // Optional: Add timeout handler
        webAsyncTask.onTimeout(() -> {
            log.warn("Video conversion timed out after 30 minutes");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .build();
        });

        return webAsyncTask;
    }

    private Path findMainM3u8File(Path hlsBaseDir) {
        try {
            // Look for the main playlist file
            List<Path> m3u8Files = Files.walk(hlsBaseDir)
                    .filter(path -> path.toString().toLowerCase().endsWith(".m3u8"))
                    .collect(Collectors.toList());

            log.info("Found " + m3u8Files.size() + " m3u8 files: " + m3u8Files);

            // Try to find master playlist first
            for (Path m3u8File : m3u8Files) {
                try {
                    String content = Files.readString(m3u8File);
                    if (content.contains("#EXT-X-STREAM-INF")) {
                        log.info("Found master playlist: " + m3u8File);
                        return m3u8File;
                    }
                } catch (IOException e) {
                    log.warn("Could not read m3u8 file: " + m3u8File, e);
                }
            }

            // If no master playlist found, look for media playlist
            for (Path m3u8File : m3u8Files) {
                try {
                    String content = Files.readString(m3u8File);
                    if (content.contains("#EXTINF")) {
                        log.info("Found media playlist: " + m3u8File);
                        return m3u8File;
                    }
                } catch (IOException e) {
                    log.warn("Could not read m3u8 file: " + m3u8File, e);
                }
            }

            // Fallback to first m3u8 file found
            if (!m3u8Files.isEmpty()) {
                log.info("Using first m3u8 file found: " + m3u8Files.get(0));
                return m3u8Files.get(0);
            }

            return null;
        } catch (IOException e) {
            log.error("Error searching for m3u8 file", e);
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
}