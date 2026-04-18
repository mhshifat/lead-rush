package com.leadrush.chat.controller;

import com.leadrush.config.LeadRushProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the built chat widget bundle at GET /widget.js.
 *
 * Resolution:
 *   1. If the file is packaged in the backend jar (static/widget.js), serve it.
 *   2. In dev, fall back to the built file at ../widget/dist/widget.js on disk.
 *   3. Otherwise 404 — the user needs to run `npm run build` in widget/.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WidgetScriptController {

    private final LeadRushProperties properties;

    @GetMapping(value = "/widget.js", produces = "application/javascript")
    public ResponseEntity<byte[]> widget() throws Exception {
        byte[] bytes = loadWidget();
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .header("Cache-Control", "public, max-age=300")
                .header("Access-Control-Allow-Origin", "*")
                .body(bytes);
    }

    private byte[] loadWidget() {
        // 1. Classpath (packaged jar)
        try {
            Resource resource = new org.springframework.core.io.ClassPathResource("static/widget.js");
            if (resource.exists()) {
                return resource.getInputStream().readAllBytes();
            }
        } catch (Exception ignored) {}

        // 2. Dev fallback — built file next to the backend project
        for (String candidate : new String[] {
                "../widget/dist/widget.js",
                "widget/dist/widget.js",
        }) {
            try {
                Path path = Paths.get(candidate).toAbsolutePath().normalize();
                if (Files.exists(path)) return Files.readAllBytes(path);
            } catch (Exception ignored) {}
        }

        log.warn("/widget.js not found — run `npm run build` in widget/");
        return null;
    }
}
