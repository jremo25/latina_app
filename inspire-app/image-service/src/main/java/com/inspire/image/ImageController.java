package com.inspire.image;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/image")
@CrossOrigin(origins = "*")   // CORS on the controller — no separate config class needed,
                               // which means @WebMvcTest loads cleanly without extra imports
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * GET /api/image
     * Picks a random image and returns its serving URL.
     * Response: { "url": "/api/image/file/photo2.jpg" }
     */
    @GetMapping
    public Map<String, String> getRandomImage() {
        String filename = imageService.getRandomImageFilename();
        return Map.of("url", "/api/image/file/" + filename);
    }

    /**
     * GET /api/image/file/{filename}
     * Streams the actual image bytes from the classpath.
     * Content-Type is inferred from the file extension.
     */
    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        Resource resource = imageService.getImageResource(filename);
        MediaType mediaType = filename.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }
}
