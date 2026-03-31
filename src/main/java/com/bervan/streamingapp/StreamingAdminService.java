package com.bervan.streamingapp;

import com.bervan.filestorage.model.BervanMockMultiPartFile;
import com.bervan.filestorage.service.FileServiceManager;
import com.bervan.logging.JsonLogger;
import com.bervan.streamingapp.config.ProductionData;
import com.bervan.streamingapp.config.StreamingConfigLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class StreamingAdminService {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");
    private final FileServiceManager fileServiceManager;
    private final VideoManager videoManager;
    private final StreamingConfigLoader streamingConfigLoader;

    public StreamingAdminService(FileServiceManager fileServiceManager, VideoManager videoManager,
                                  StreamingConfigLoader streamingConfigLoader) {
        this.fileServiceManager = fileServiceManager;
        this.videoManager = videoManager;
        this.streamingConfigLoader = streamingConfigLoader;
    }

    public void createProduction(String name, String type, String videoFormat, String description,
                                  Double rating, Integer yearStart, Integer yearEnd, String country,
                                  String categories, String tags,
                                  byte[] posterBytes, String posterFilename) throws Exception {
        String rootPath = videoManager.appFolder + File.separator;

        fileServiceManager.createEmptyDirectory(rootPath, name);
        log.info("Created production folder: {}", name);

        String productionPath = rootPath + name + File.separator;
        String detailsJson = buildDetailsJson(name, type, videoFormat, description, rating, yearStart, yearEnd, country, categories, tags);
        InputStream jsonStream = new ByteArrayInputStream(detailsJson.getBytes(StandardCharsets.UTF_8));
        BervanMockMultiPartFile jsonFile = new BervanMockMultiPartFile("details.json", "details.json", "application/json", jsonStream);
        fileServiceManager.save(jsonFile, "", productionPath);
        log.info("Saved details.json for production: {}", name);

        if (posterBytes != null && posterBytes.length > 0) {
            String filename = resolveImageFilename(posterFilename);
            InputStream posterStream = new ByteArrayInputStream(posterBytes);
            BervanMockMultiPartFile posterFile = new BervanMockMultiPartFile(filename, filename, "image/jpeg", posterStream);
            fileServiceManager.save(posterFile, "", productionPath);
            log.info("Saved poster for production: {}", name);
        }
    }

    public void createSeason(String productionName, int seasonNumber) {
        String productionPath = videoManager.appFolder + File.separator + productionName + File.separator;
        fileServiceManager.createEmptyDirectory(productionPath, "Season " + seasonNumber);
        log.info("Created Season {} for production: {}", seasonNumber, productionName);
    }

    public void addEpisodeMP4(String productionName, int seasonNumber, int episodeNumber,
                               InputStream videoStream, String videoFilename) throws Exception {
        String seasonPath = videoManager.appFolder + File.separator + productionName + File.separator
                + "Season " + seasonNumber + File.separator;
        String episodeFolderName = String.format("S%02dE%02d", seasonNumber, episodeNumber);

        fileServiceManager.createEmptyDirectory(seasonPath, episodeFolderName);
        log.info("Created episode folder {} in season {}", episodeFolderName, seasonNumber);

        String episodePath = seasonPath + episodeFolderName + File.separator;
        BervanMockMultiPartFile videoFile = new BervanMockMultiPartFile(videoFilename, videoFilename, "video/mp4", videoStream);
        fileServiceManager.save(videoFile, "", episodePath);
        log.info("Saved episode video {} in {}", videoFilename, episodePath);
    }

    public void addEpisodeHLS(String productionName, int seasonNumber,
                               InputStream zipStream, String zipFilename) throws Exception {
        String seasonPath = videoManager.appFolder + File.separator + productionName + File.separator
                + "Season " + seasonNumber + File.separator;
        BervanMockMultiPartFile zipFile = new BervanMockMultiPartFile(zipFilename, zipFilename, "application/zip", zipStream);
        fileServiceManager.saveAndExtractZip(zipFile, "", seasonPath);
        log.info("Extracted HLS ZIP {} to season {}", zipFilename, seasonNumber);
    }

    public void addMovieVideoMP4(String productionName, InputStream videoStream, String videoFilename) throws Exception {
        String productionPath = videoManager.appFolder + File.separator + productionName + File.separator;
        BervanMockMultiPartFile videoFile = new BervanMockMultiPartFile(videoFilename, videoFilename, "video/mp4", videoStream);
        fileServiceManager.save(videoFile, "", productionPath);
        log.info("Saved movie video {} for production: {}", videoFilename, productionName);
    }

    public void addSubtitlesFromZip(String productionName, InputStream zipStream) throws Exception {
        Pattern subtitlePattern = Pattern.compile("(?i)s(\\d+)e(\\d+)");

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String filename = java.nio.file.Path.of(entryName).getFileName().toString();
                if (!filename.endsWith(".srt") && !filename.endsWith(".vtt")) {
                    zis.closeEntry();
                    continue;
                }

                Matcher matcher = subtitlePattern.matcher(filename);
                if (!matcher.find()) {
                    log.warn("Could not parse season/episode from subtitle filename: {}", filename);
                    zis.closeEntry();
                    continue;
                }

                int season = Integer.parseInt(matcher.group(1));
                int episode = Integer.parseInt(matcher.group(2));
                String episodeFolderName = String.format("S%02dE%02d", season, episode);
                String targetPath = videoManager.appFolder + File.separator + productionName
                        + File.separator + "Season " + season
                        + File.separator + episodeFolderName + File.separator;

                byte[] fileBytes = zis.readAllBytes();
                BervanMockMultiPartFile subtitleFile = new BervanMockMultiPartFile(
                        filename, filename, "text/plain", new ByteArrayInputStream(fileBytes));
                fileServiceManager.save(subtitleFile, "", targetPath);
                log.info("Saved subtitle {} to {}", filename, targetPath);

                zis.closeEntry();
            }
        }
    }

    public void reloadConfig(Map<String, ProductionData> streamingProductionData) {
        Map<String, ProductionData> newData = streamingConfigLoader.getStringProductionDataMap();
        streamingProductionData.clear();
        streamingProductionData.putAll(newData);
        log.info("Config reloaded, productions count: {}", streamingProductionData.size());
    }

    private String resolveImageFilename(String posterFilename) {
        if (posterFilename != null && (posterFilename.toLowerCase().endsWith(".jpg")
                || posterFilename.toLowerCase().endsWith(".jpeg"))) {
            return "poster.jpg";
        }
        return "poster.png";
    }

    private String buildDetailsJson(String name, String type, String videoFormat, String description,
                                     Double rating, Integer yearStart, Integer yearEnd,
                                     String country, String categories, String tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(escapeJson(name)).append("\",\n");
        sb.append("  \"description\": \"").append(escapeJson(description != null ? description : "")).append("\",\n");
        sb.append("  \"type\": \"").append(escapeJson(type != null ? type : "movie")).append("\",\n");
        sb.append("  \"videoFormat\": \"").append(escapeJson(videoFormat != null ? videoFormat : "mp4")).append("\",\n");
        if (rating != null) {
            sb.append("  \"rating\": ").append(rating).append(",\n");
        }
        if (yearStart != null) {
            sb.append("  \"releaseYearStart\": ").append(yearStart).append(",\n");
        }
        if (yearEnd != null) {
            sb.append("  \"releaseYearEnd\": ").append(yearEnd).append(",\n");
        }
        if (country != null && !country.isBlank()) {
            sb.append("  \"country\": \"").append(escapeJson(country)).append("\",\n");
        }
        sb.append("  \"categories\": [");
        if (categories != null && !categories.isBlank()) {
            String[] catArr = categories.split(",");
            for (int i = 0; i < catArr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(catArr[i].trim())).append("\"");
            }
        }
        sb.append("],\n");
        sb.append("  \"tags\": [");
        if (tags != null && !tags.isBlank()) {
            String[] tagArr = tags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(tagArr[i].trim())).append("\"");
            }
        }
        sb.append("]\n");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
