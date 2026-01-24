package com.schoolable.backend.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeeklyReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class WeeklyReportControllerTest {

    private static final UUID USER_ID = UUID.fromString("13131313-1313-1313-1313-131313131313");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WeeklyReportService weeklyReportService;

    @Test
    void submitWeeklyReport_returnsCreated() throws Exception {
        WeeklyReportDto.ReportResponse response = new WeeklyReportDto.ReportResponse();
        response.setEmployeeId("employee-1");
        response.setWeekNumber(2);
        response.setYear(2026);

        when(weeklyReportService.submitReport(eq(USER_ID), any(WeeklyReportDto.SingleReportRequest.class)))
            .thenReturn(response);

        String payload = """
            {
              "employeeId": "employee-1",
              "weekNumber": 2,
              "year": 2026,
              "technicalScore": 4,
              "behavioralScore": 4,
              "cultureFitScore": 4,
              "growthLearningScore": 4
            }
            """;

        mockMvc.perform(post("/api/performance/weekly")
                .header("X-User-ID", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)));
    }
}
