package com.schoolable.backend.kpi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KpiController.class)
@AutoConfigureMockMvc(addFilters = false)
class KpiControllerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KpiAnalysisService kpiService;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private AiInsightRepository insightRepository;

    @MockBean
    private TeamQuarterlyScoreRepository scoreRepository;

    @MockBean
    private TeamKpiRepository teamKpiRepository;

    @MockBean
    private PersonalInsightsService personalInsightsService;

    @MockBean
    private KpiLockService kpiLockService;

    @MockBean
    private KpiChangeRequestRepository changeRequestRepository;

    @Test
    void getMyKpis_returnsRemainingWeight() throws Exception {
        TeamKpi first = new TeamKpi(USER_ID, "Sales", new BigDecimal("10"), 30, "Q1", 2026);
        TeamKpi second = new TeamKpi(USER_ID, "Revenue", new BigDecimal("20"), 40, "Q1", 2026);

        when(kpiService.getKpisForTeamLead(eq(USER_ID), eq("Q1"), eq(2026)))
            .thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/kpi/my-kpis")
                .param("quarter", "Q1")
                .param("year", "2026")
                .principal(userAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalWeight", is(70)))
            .andExpect(jsonPath("$.remainingWeight", is(30)));
    }

    @Test
    void getAllWeeklyInsights_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/kpi/insights/all")
                .param("weekNumber", "2")
                .param("year", "2026")
                .principal(userAuth()))
            .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }

    @SuppressWarnings("unused")
    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            USER_ID,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
