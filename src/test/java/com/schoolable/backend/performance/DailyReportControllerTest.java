package com.schoolable.backend.performance;

import com.schoolable.backend.attendance.AttendancePolicyService;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class DailyReportControllerTest {

    private static final UUID USER_ID = UUID.fromString("12121212-1212-1212-1212-121212121212");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DailyReportRepository dailyReportRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private DailyReportAiService dailyReportAiService;

    @MockBean
    private AttendancePolicyService attendancePolicyService;

    @Test
    void getMyReports_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/daily-reports/my"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getTodayReport_returnsStatus() throws Exception {
        when(attendancePolicyService.resolveZone(any(), any())).thenReturn(ZoneId.of("UTC"));
        when(dailyReportRepository.findByEmployeeIdAndReportDate(eq(USER_ID), any(LocalDate.class)))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/daily-reports/today")
                .principal(userAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasSubmittedToday", is(false)));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
