package com.schoolable.backend.e2e;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.kpi.TeamKpi;
import com.schoolable.backend.kpi.TeamKpiRepository;
import com.schoolable.backend.kpi.WeeklyKpiProgressRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class TeamKpiE2ETest extends BaseE2ETest {

    @Autowired
    private TeamKpiRepository teamKpiRepository;

    @Autowired
    private WeeklyKpiProgressRepository weeklyKpiProgressRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        weeklyKpiProgressRepository.deleteAll();
        teamKpiRepository.deleteAll();
    }

    @Test
    void kpiProgressSubmissionRoundTrip() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        String quarter = currentQuarter();
        int year = LocalDate.now().getYear();

        TeamKpi kpi = new TeamKpi();
        kpi.setTeamLeadId(userId);
        kpi.setName("Customer Satisfaction");
        kpi.setTargetValue(BigDecimal.valueOf(100));
        kpi.setTargetUnit("percent");
        kpi.setWeight(25);
        kpi.setQuarter(quarter);
        kpi.setYear(year);
        teamKpiRepository.save(kpi);

        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(get("/api/kpi/my-kpis")
                .param("quarter", quarter)
                .param("year", String.valueOf(year))
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis", hasSize(1)))
            .andExpect(jsonPath("$.totalWeight").value(25));

        Map<String, Object> payload = Map.of(
            "weekNumber", 1,
            "year", year,
            "progress", List.of(Map.of(
                "kpiId", kpi.getId().toString(),
                "achievedValue", 10.0,
                "notes", "On track"
            ))
        );

        mockMvc.perform(post("/api/kpi/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.progress", hasSize(1)));
    }

    private String currentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }
}
