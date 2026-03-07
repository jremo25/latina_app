package com.inspire.phrase;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PhraseController {

    private final PhraseService phraseService;

    public PhraseController(PhraseService phraseService) {
        this.phraseService = phraseService;
    }

    /**
     * GET /api/phrase
     * Returns a randomly selected phrase as JSON: { "phrase": "..." }
     */
    @GetMapping("/phrase")
    public Map<String, String> getPhrase() {
        return Map.of("phrase", phraseService.getRandomPhrase());
    }
}
