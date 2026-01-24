package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuraExplanationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuraExplanationControllerTest {

    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AutoAuraCalculationService auraCalculationService;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void getScoreExplanation_returnsNotFound() throws Exception {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/aura/explanation/{employeeId}", USER_ID))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMyScoreExplanation_returnsExplanation() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setFullName("Test User");
        profile.setDepartment("Sales");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        Map<String, Object> pillar = new HashMap<>();
        pillar.put("name", "Technical");
        pillar.put("score", 70.0);
        pillar.put("weight", 25.0);
        pillar.put("contribution", 17.5);
        pillar.put("dataSource", "auto");
        pillar.put("subMetrics", List.of());

        Map<String, Object> scoreData = new HashMap<>();
        scoreData.put("auraScore", 75.0);
        scoreData.put("grade", "B");
        scoreData.put("qgpa", 3.5);
        scoreData.put("pillars", Map.of("technical", pillar));

        when(auraCalculationService.calculateEmployeeScore(profile)).thenReturn(scoreData);

        mockMvc.perform(get("/aura/explanation/me")
                .principal(userAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employeeName", is("Test User")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
