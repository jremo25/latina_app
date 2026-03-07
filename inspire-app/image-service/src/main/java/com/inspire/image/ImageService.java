package com.inspire.image;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ImageService {

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    /**
     * Lists all filenames found inside classpath:images/.
     * Images are bundled into the JAR at build time from
     * src/main/resources/images/.
     * Adding a new file and rebuilding makes it available — no code changes needed.
     */
    public List<String> listImageFilenames() {
        try {
            Resource[] resources = resolver.getResources("classpath:images/*");
            return Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ImageServiceException("Failed to list images from classpath", e);
        }
    }

    /**
     * Returns the filename of one image chosen uniformly at random.
     * Throws if no images are available.
     */
    public String getRandomImageFilename() {
        List<String> filenames = listImageFilenames();
        if (filenames.isEmpty()) {
            throw new ImageServiceException("No images found in classpath:images/", null);
        }
        int index = ThreadLocalRandom.current().nextInt(filenames.size());
        return filenames.get(index);
    }

    /**
     * Returns the Spring Resource for a given filename from classpath:images/.
     */
    public Resource getImageResource(String filename) {
        Resource resource = resolver.getResource("classpath:images/" + filename);
        if (!resource.exists()) {
            throw new ImageNotFoundException("Image not found: " + filename);
        }
        return resource;
    }
}
