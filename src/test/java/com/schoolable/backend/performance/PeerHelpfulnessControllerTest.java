package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeerHelpfulnessController.class)
@AutoConfigureMockMvc(addFilters = false)
class PeerHelpfulnessControllerTest {

    private static final UUID USER_ID = UUID.fromString("17171717-1717-1717-1717-171717171717");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PeerHelpfulnessRepository helpfulnessRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void submitRatings_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/peer-helpfulness/submit")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void submitRatings_returnsErrorsForInvalidRating() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setDepartment("Sales");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        String payload = """
            {"ratings":[{"userId":"18181818-1818-1818-1818-181818181818","rating":6,"comment":"test"}]}
            """;

        mockMvc.perform(post("/api/peer-helpfulness/submit")
                .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    USER_ID, "n/a", List.of()))
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errors", hasSize(1)));
    }
}
