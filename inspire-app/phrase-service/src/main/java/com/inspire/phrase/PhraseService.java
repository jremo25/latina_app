package com.inspire.phrase;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PhraseService {

    private static final List<String> PHRASES = List.of(
        "Every picture tells a story.",
        "A moment frozen in time.",
        "Beauty is in the eye of the beholder.",
        "The world is full of magic things, patiently waiting for our senses to grow sharper.",
        "Not all those who wander are lost.",
        "Simplicity is the ultimate sophistication.",
        "Life is what happens between the photos.",
        "Capture the moment, keep the memory.",
        "In every image, a thousand words.",
        "The eye sees only what the mind is prepared to comprehend.",
        "Wonder is the beginning of wisdom.",
        "Look deep into nature and you will understand everything better.",
        "Light is the first touch of the world upon our eyes.",
        "To photograph is to participate in another person's mortality.",
        "A picture is a secret about a secret, the more it tells you the less you know."
    );

    /**
     * Returns the full list of available phrases.
     * Exposed for testability — tests can verify list size and contents.
     */
    public List<String> getPhrases() {
        return PHRASES;
    }

    /**
     * Returns one phrase chosen uniformly at random from the list.
     */
    public String getRandomPhrase() {
        int index = ThreadLocalRandom.current().nextInt(PHRASES.size());
        return PHRASES.get(index);
    }
}
