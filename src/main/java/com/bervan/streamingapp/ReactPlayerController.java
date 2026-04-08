package com.bervan.streamingapp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Serves the React streaming frontend SPA.
 * Built assets live in static/streaming-react/ (classpath, from streaming-platform-react JAR).
 * Entry point: GET /api/streaming/react-player/
 */
@RestController
@RequestMapping("/api/streaming/react-player")
public class ReactPlayerController {

    private static final String CLASSPATH_BASE = "static/streaming-react/";

    @GetMapping({"", "/"})
    public ResponseEntity<byte[]> serveRoot() throws IOException {
        return serveFile("index.html");
    }

    @GetMapping("/{*path}")
    public ResponseEntity<byte[]> serveResource(@PathVariable String path) throws IOException {
        // Strip leading slash injected by Spring
        String filePath = path.startsWith("/") ? path.substring(1) : path;

        // No file extension → SPA route, serve index.html
        if (!filePath.contains(".")) {
            return serveFile("index.html");
        }

        ClassPathResource resource = new ClassPathResource(CLASSPATH_BASE + filePath);
        if (!resource.exists()) {
            // Unknown asset → still serve index.html so SPA can handle 404
            return serveFile("index.html");
        }

        byte[] content = resource.getInputStream().readAllBytes();
        return ResponseEntity.ok()
                .header("Content-Type", detectContentType(filePath))
                .header("Cache-Control", "max-age=31536000, immutable")
                .body(content);
    }

    private ResponseEntity<byte[]> serveFile(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_BASE + filename);
        if (!resource.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        byte[] content = resource.getInputStream().readAllBytes();
        return ResponseEntity.ok()
                .header("Content-Type", detectContentType(filename))
                .header("Cache-Control", "no-cache")
                .body(content);
    }

    private String detectContentType(String filename) {
        if (filename.endsWith(".js"))               return "application/javascript; charset=utf-8";
        if (filename.endsWith(".css"))              return "text/css; charset=utf-8";
        if (filename.endsWith(".html"))             return "text/html; charset=utf-8";
        if (filename.endsWith(".json"))             return "application/json; charset=utf-8";
        if (filename.endsWith(".svg"))              return "image/svg+xml";
        if (filename.endsWith(".png"))              return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".ico"))              return "image/x-icon";
        if (filename.endsWith(".woff2"))            return "font/woff2";
        if (filename.endsWith(".woff"))             return "font/woff";
        return "application/octet-stream";
    }
}
