package com.schoolable.backend.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.performance.WeeklyReportRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WeeklyReportsE2ETest extends BaseE2ETest {

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @BeforeEach
    void cleanup() {
        weeklyReportRepository.deleteAll();
    }

    @Test
    void weeklyReportsReturnsEmptyList() throws Exception {
        int year = LocalDate.now().getYear();

        mockMvc.perform(get("/api/performance/weekly")
                .param("week", "1")
                .param("year", String.valueOf(year)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.reports").isArray());
    }
}
