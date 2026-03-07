package com.inspire.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ImageService.
 * No Spring context — instantiates ImageService directly.
 * Written RED first, then GREEN, then REFACTOR.
 */
class ImageServiceTest {

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService();
    }

    @Test
    void listImageFilenames_withImagesInClasspath_returnsNonEmptyList() {
        List<String> filenames = imageService.listImageFilenames();
        assertThat(filenames).isNotEmpty();
    }

    @Test
    void listImageFilenames_called_allFilenamesAreNonBlank() {
        List<String> filenames = imageService.listImageFilenames();
        assertThat(filenames).allMatch(name -> name != null && !name.isBlank());
    }

    @Test
    void listImageFilenames_called_containsKnownTestImage() {
        // aurora.jpg is bundled in src/main/resources/images/
        List<String> filenames = imageService.listImageFilenames();
        assertThat(filenames).contains("image1.jpg");
    }

    @Test
    void getRandomImageFilename_called_returnsValuePresentInList() {
        String filename = imageService.getRandomImageFilename();
        assertThat(imageService.listImageFilenames()).contains(filename);
    }

    @Test
    void getRandomImageFilename_called_returnsNonBlankFilename() {
        String filename = imageService.getRandomImageFilename();
        assertThat(filename).isNotBlank();
    }

    @Test
    void getRandomImageFilename_calledManyTimes_returnsMoreThanOneDistinctValue() {
        // With 5 images and 50 calls, the probability of always picking
        // the same image is (1/5)^49 ≈ 0 — effectively impossible for a
        // correct uniform random implementation.
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            results.add(imageService.getRandomImageFilename());
        }
        assertThat(results.size()).isGreaterThan(1);
    }

    @Test
    void getImageResource_knownFilename_returnsExistingResource() {
        var resource = imageService.getImageResource("image1.jpg");
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }
}
