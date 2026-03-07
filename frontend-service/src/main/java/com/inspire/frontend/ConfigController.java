package com.inspire.frontend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ConfigController {

    @Value("${image.service.url}")
    private String imageServiceUrl;

    @Value("${phrase.service.url}")
    private String phraseServiceUrl;

    /**
     * GET /api/config
     * Returns the URLs of the two backend microservices so the browser
     * can call them directly. Values come from application.properties
     * and are overridden at runtime by IMAGE_SERVICE_URL / PHRASE_SERVICE_URL
     * environment variables (Spring Boot relaxed binding).
     */
    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return Map.of(
                "imageServiceUrl", imageServiceUrl,
                "phraseServiceUrl", phraseServiceUrl
        );
    }
}
