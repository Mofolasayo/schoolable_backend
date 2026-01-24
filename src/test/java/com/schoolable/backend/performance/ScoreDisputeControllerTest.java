package com.schoolable.backend.performance;

import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScoreDisputeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoreDisputeControllerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScoreDisputeRepository disputeRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private AuraScoreJobService auraScoreJobService;

    @Test
    void submitDispute_requiresFields() throws Exception {
        String payload = """
            {"scoreType":"aura","disputedScore":null,"reason":null}
            """;

        mockMvc.perform(post("/api/score-disputes")
                .principal(userAuth())
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("scoreType, disputedScore, and reason are required")));
    }

    @Test
    void getPendingDisputes_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/score-disputes/pending")
                .principal(userAuth()))
            .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
