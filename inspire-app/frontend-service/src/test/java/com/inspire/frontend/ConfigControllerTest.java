package com.inspire.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer slice tests for ConfigController.
 *
 * @TestPropertySource injects known URL values so tests can assert exact
 * response values rather than whatever is in application.properties.
 */
@WebMvcTest(controllers = ConfigController.class,
            excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
            })
@TestPropertySource(properties = {
        "image.service.url=http://test-image-service",
        "phrase.service.url=http://test-phrase-service"
})
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getConfig_called_returns200() throws Exception {
        mockMvc.perform(get("/api/config"))
               .andExpect(status().isOk());
    }

    @Test
    void getConfig_called_returnsJsonContentType() throws Exception {
        mockMvc.perform(get("/api/config"))
               .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void getConfig_called_responseContainsImageServiceUrlField() throws Exception {
        mockMvc.perform(get("/api/config"))
               .andExpect(jsonPath("$.imageServiceUrl").exists());
    }

    @Test
    void getConfig_called_responseContainsPhraseServiceUrlField() throws Exception {
        mockMvc.perform(get("/api/config"))
               .andExpect(jsonPath("$.phraseServiceUrl").exists());
    }

    @Test
    void getConfig_withTestProperties_imageUrlMatchesInjectedValue() throws Exception {
        mockMvc.perform(get("/api/config"))
               .andExpect(jsonPath("$.imageServiceUrl").value("http://test-image-service"));
    }

    @Test
    void getConfig_withTestProperties_phraseUrlMatchesInjectedValue() throws Exception {
        mockMvc.perform(get("/api/config"))
               .andExpect(jsonPath("$.phraseServiceUrl").value("http://test-phrase-service"));
    }
}
