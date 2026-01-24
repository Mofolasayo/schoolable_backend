package com.schoolable.backend.performance;

import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeerFeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
class PeerFeedbackControllerTest {

    private static final UUID TARGET_ID = UUID.fromString("15151515-1515-1515-1515-151515151515");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PeerFeedbackRepository peerFeedbackRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void submitFeedback_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/performance/peer-feedback")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void submitFeedback_rejectsInvalidRating() throws Exception {
        String payload = """
            {"toEmployeeId":"%s","supportRating":0}
            """.formatted(TARGET_ID);

        mockMvc.perform(post("/api/performance/peer-feedback")
                .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    UUID.fromString("16161616-1616-1616-1616-161616161616"), "n/a", java.util.List.of()))
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("supportRating must be 1-5")));
    }
}
