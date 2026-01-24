package com.schoolable.backend.e2e;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.performance.DailyReportRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class DailyReportsE2ETest extends BaseE2ETest {

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @BeforeEach
    void cleanup() {
        dailyReportRepository.deleteAll();
    }

    @Test
    void myReportsReturnsEmptyList() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(get("/api/daily-reports/my")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void todayReportStatusDefaults() throws Exception {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(get("/api/daily-reports/today")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasSubmittedToday").value(false))
            .andExpect(jsonPath("$.submissionWindow").exists());
    }
}
