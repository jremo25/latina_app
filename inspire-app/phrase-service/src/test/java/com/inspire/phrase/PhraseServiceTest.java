package com.inspire.phrase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PhraseService.
 * No Spring context — instantiates PhraseService directly.
 * Written RED first, then GREEN, then REFACTOR.
 */
class PhraseServiceTest {

    private PhraseService phraseService;

    @BeforeEach
    void setUp() {
        phraseService = new PhraseService();
    }

    @Test
    void getPhrases_called_returnsNonEmptyList() {
        assertThat(phraseService.getPhrases()).isNotEmpty();
    }

    @Test
    void getPhrases_called_containsAtLeastTenPhrases() {
        assertThat(phraseService.getPhrases().size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void getRandomPhrase_called_returnsStringContainedInPhraseList() {
        String result = phraseService.getRandomPhrase();
        assertThat(phraseService.getPhrases()).contains(result);
    }

    @Test
    void getRandomPhrase_called_returnsNonBlankString() {
        String result = phraseService.getRandomPhrase();
        assertThat(result).isNotBlank();
    }

    @Test
    void getRandomPhrase_calledManyTimes_doesNotAlwaysReturnSamePhrase() {
        // Over 50 calls, a uniform random selection across 15+ phrases
        // should produce more than 1 distinct result. Probability of
        // failure for a correct implementation is astronomically small.
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            results.add(phraseService.getRandomPhrase());
        }
        assertThat(results.size()).isGreaterThan(1);
    }
}
