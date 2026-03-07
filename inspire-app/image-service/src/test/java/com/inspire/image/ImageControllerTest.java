package com.inspire.image;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer slice tests for ImageController.
 *
 * Uses @WebMvcTest(ImageController.class) — only the controller and its
 * direct MVC wiring are loaded. ImageService is replaced with a Mockito mock
 * via @MockBean so no classpath scanning or real file I/O happens here.
 *
 * CORS is declared with @CrossOrigin directly on the controller (not in a
 * separate CorsConfig class) so @WebMvcTest does not encounter any
 * WebMvcConfigurer beans that could interfere with the test context.
 */
@WebMvcTest(controllers = ImageController.class,
            excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
            })
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImageService imageService;

    /** Minimal in-memory resource — no real files needed in test scope */
    private static final Resource FAKE_JPEG =
        new ByteArrayResource(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}) {
            @Override public String getFilename() { return "aurora.jpg"; }
        };

    @Test
    void getImage_called_returns200() throws Exception {
        given(imageService.getRandomImageFilename()).willReturn("aurora.jpg");

        mockMvc.perform(get("/api/image"))
               .andExpect(status().isOk());
    }

    @Test
    void getImage_called_returnsJsonWithUrlField() throws Exception {
        given(imageService.getRandomImageFilename()).willReturn("aurora.jpg");

        mockMvc.perform(get("/api/image"))
               .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void getImage_serviceReturnsFilename_urlContainsThatFilename() throws Exception {
        given(imageService.getRandomImageFilename()).willReturn("ocean.jpg");

        mockMvc.perform(get("/api/image"))
               .andExpect(jsonPath("$.url").value(containsString("ocean.jpg")));
    }

    @Test
    void getImage_serviceReturnsFilename_urlPointsToFileEndpoint() throws Exception {
        given(imageService.getRandomImageFilename()).willReturn("forest.jpg");

        mockMvc.perform(get("/api/image"))
               .andExpect(jsonPath("$.url").value("/api/image/file/forest.jpg"));
    }

    @Test
    void serveImage_knownFile_returns200() throws Exception {
        given(imageService.getImageResource("aurora.jpg")).willReturn(FAKE_JPEG);

        mockMvc.perform(get("/api/image/file/aurora.jpg"))
               .andExpect(status().isOk());
    }

    @Test
    void serveImage_jpegFile_returnsImageJpegContentType() throws Exception {
        given(imageService.getImageResource("desert.jpg")).willReturn(FAKE_JPEG);

        mockMvc.perform(get("/api/image/file/desert.jpg"))
               .andExpect(content().contentTypeCompatibleWith("image/jpeg"));
    }
}
