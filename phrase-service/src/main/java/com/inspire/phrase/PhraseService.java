package com.inspire.phrase;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PhraseService {

  private static final List<String> PHRASES = List.of(
    "She moves like the rest of the world is standing still.",
    "Dark eyes that know everything and reveal just enough.",
    "There is fire in her stillness and grace in her fury.",
    "Her beauty does not ask to be noticed — it simply cannot be ignored.",
    "Warm skin like the last hour of sunlight, impossible to look away from.",
    "She carries centuries of passion in the way she holds herself.",
    "A single glance from her rewrites everything you thought you knew.",
    "Her curves are a language spoken only by those paying close attention.",
    "She is the kind of beautiful that lingers long after she has left the room.",
    "Softness and strength woven together so tightly you cannot tell where one ends.",
    "Her laugh arrives before she does and stays long after she is gone.",
    "To be looked at by her is to feel briefly, magnificently seen.",
    "She does not glow — she burns, steady and warm and utterly consuming.",
    "There is nothing accidental about the way she takes up space.",
    "She is the moment just before the sun disappears — vivid, irreversible, unforgettable."
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
