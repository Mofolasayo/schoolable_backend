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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuraDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuraDashboardControllerTest {

    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuraDashboardService auraDashboardService;

    @MockBean
    private EnhancedAuraService enhancedAuraService;

    @MockBean
    private SubMetricCalculationService calculationService;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private AutoAuraCalculationService autoAuraService;

    @MockBean
    private AuraScoreJobService auraScoreJobService;

    @Test
    void getMyAura_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/performance/my-aura"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyAura_returnsDashboard() throws Exception {
        AuraDashboardDto.EmployeeAuraResponse response = new AuraDashboardDto.EmployeeAuraResponse();
        response.setEmployeeId(USER_ID.toString());
        response.setAuraScore(80.0);

        when(auraDashboardService.getEmployeeAuraDashboard(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/performance/my-aura")
                .principal(userAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auraScore", is(80.0)));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
