package com.schoolable.backend.attendance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LateAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class LateAnalyticsControllerTest {

    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttendanceRepository attendanceRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private AttendancePolicyService attendancePolicyService;

    @Test
    void getLateAnalytics_requiresAdmin() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setRole("employee");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/api/admin/late-analytics")
                .principal(auth(USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    void getLateAnalytics_returnsSummaryForAdmin() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setRole("admin");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(attendanceRepository.findByDateRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/late-analytics")
                .principal(auth(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.totalLateCheckIns", is(0)))
            .andExpect(jsonPath("$.summary.onTimeRate", is(100.0)));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }
}
