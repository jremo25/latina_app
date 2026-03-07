package com.inspire.phrase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer slice tests for PhraseController.
 *
 * Security auto-configuration is excluded — this app has no security
 * dependencies, but excluding explicitly prevents Boot from attempting
 * to wire a UserDetailsService and failing the context load.
 */
@WebMvcTest(controllers = PhraseController.class,
            excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
            })
class PhraseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PhraseService phraseService;

    @Test
    void getPhrase_called_returns200() throws Exception {
        given(phraseService.getRandomPhrase()).willReturn("Every picture tells a story.");

        mockMvc.perform(get("/api/phrase"))
               .andExpect(status().isOk());
    }

    @Test
    void getPhrase_called_returnsJsonContentType() throws Exception {
        given(phraseService.getRandomPhrase()).willReturn("Every picture tells a story.");

        mockMvc.perform(get("/api/phrase"))
               .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void getPhrase_called_responseContainsPhraseField() throws Exception {
        given(phraseService.getRandomPhrase()).willReturn("Every picture tells a story.");

        mockMvc.perform(get("/api/phrase"))
               .andExpect(jsonPath("$.phrase").exists());
    }

    @Test
    void getPhrase_serviceReturnsPhrase_phraseFieldMatchesServiceResponse() throws Exception {
        given(phraseService.getRandomPhrase()).willReturn("Every picture tells a story.");

        mockMvc.perform(get("/api/phrase"))
               .andExpect(jsonPath("$.phrase").value("Every picture tells a story."));
    }

    @Test
    void getPhrase_called_phraseFieldIsNotBlank() throws Exception {
        given(phraseService.getRandomPhrase()).willReturn("Some phrase.");

        mockMvc.perform(get("/api/phrase"))
               .andExpect(jsonPath("$.phrase").isNotEmpty());
    }
}
