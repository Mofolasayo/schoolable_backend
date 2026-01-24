package com.schoolable.backend.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class AttendanceControllerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttendanceRepository attendanceRepository;

    @MockBean
    private OfficeLocationRepository officeLocationRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private AttendancePolicyService attendancePolicyService;

    @MockBean
    private BiometricConsentRepository biometricConsentRepository;

    @MockBean
    private FaceMatchService faceMatchService;

    @MockBean
    private HolidayCalendarRepository holidayCalendarRepository;

    @MockBean
    private TimeOffRequestRepository timeOffRequestRepository;

    @MockBean
    private NotificationService notificationService;

    @Test
    void checkIn_requiresAuth() throws Exception {
        mockMvc.perform(post("/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthenticated")));
    }

    @Test
    void checkIn_requiresDeviceId() throws Exception {
        mockMvc.perform(post("/attendance/check-in")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("DEVICE_ID_REQUIRED")));
    }

    @Test
    void checkIn_requiresPhotoWhenOnsite() throws Exception {
        when(attendanceRepository.findByUserIdAndDate(ArgumentMatchers.eq(USER_ID), ArgumentMatchers.any(LocalDate.class)))
            .thenReturn(Optional.empty());

        BiometricConsent consent = new BiometricConsent();
        consent.setRetentionDays(30);
        when(biometricConsentRepository.findByUserId(USER_ID)).thenReturn(Optional.of(consent));

        WorkSchedule schedule = new WorkSchedule();
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setRemoteAllowed(false);

        AttendancePolicyService.AttendancePolicy policy = new AttendancePolicyService.AttendancePolicy(
            schedule, true, false, false, null, null
        );
        when(attendancePolicyService.resolvePolicy(ArgumentMatchers.eq(USER_ID), ArgumentMatchers.any(LocalDate.class)))
            .thenReturn(policy);
        when(attendancePolicyService.validateLocation(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(new AttendancePolicyService.LocationValidation(true, true, 0, null, "HQ"));
        when(attendancePolicyService.evaluateCheckIn(ArgumentMatchers.any(), ArgumentMatchers.eq(schedule)))
            .thenReturn(new AttendancePolicyService.CheckInEvaluation(false, 0));

        mockMvc.perform(post("/attendance/check-in")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "device_id", "device-1",
                    "latitude", 6.0,
                    "longitude", 3.0,
                    "is_remote", false
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("PHOTO_REQUIRED")));

        verify(attendanceRepository, never()).save(ArgumentMatchers.any(Attendance.class));
    }

    @Test
    void checkOut_requiresCheckIn() throws Exception {
        when(attendanceRepository.findByUserIdAndDate(ArgumentMatchers.eq(USER_ID), ArgumentMatchers.any(LocalDate.class)))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/attendance/check-out")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("No check-in found for today")));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
