package com.schoolable.backend.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerformanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PerformanceControllerTest {

    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PerformanceReviewService reviewService;

    @Test
    void submitAssessment_returnsCreated() throws Exception {
        PerformanceReviewDto.ReviewResponse response = new PerformanceReviewDto.ReviewResponse();
        response.setId(1L);
        response.setEmployeeId("employee-1");

        when(reviewService.submitAssessment(eq(USER_ID), any(PerformanceReviewDto.TeamLeadAssessmentRequest.class)))
            .thenReturn(response);

        String payload = """
            {
              "employeeId": "employee-1",
              "quarter": "Q1",
              "reviewYear": 2026,
              "technicalScore": 80,
              "behavioralScore": 70,
              "cultureFitScore": 75,
              "growthLearningScore": 65,
              "submitForApproval": true
            }
            """;

        mockMvc.perform(post("/api/performance/assess")
                .header("X-User-ID", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)));
    }
}
