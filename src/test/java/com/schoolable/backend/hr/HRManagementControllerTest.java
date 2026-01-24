package com.schoolable.backend.hr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.performance.TrainingRecordRepository;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HRManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class HRManagementControllerTest {

    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID EMPLOYEE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID TEAM_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID APPOINTMENT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HRManagementService hrService;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private TrainingRecordRepository trainingRecordRepository;

    @MockBean
    private JobLevelRepository jobLevelRepository;

    @Test
    void getTeamLeads_returnsList() throws Exception {
        when(hrService.getActiveTeamLeads()).thenReturn(List.of(Map.of("id", EMPLOYEE_ID)));

        mockMvc.perform(get("/api/hr/team-leads"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id", is(EMPLOYEE_ID.toString())));
    }

    @Test
    void createTeam_returnsTeamId() throws Exception {
        Team team = new Team();
        team.setId(TEAM_ID);
        when(hrService.createTeam(eq("Sales"), eq("Core sales team"), eq(USER_ID))).thenReturn(team);

        mockMvc.perform(post("/api/hr/teams")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "name", "Sales",
                    "description", "Core sales team"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.teamId", is(TEAM_ID.toString())));
    }

    @Test
    void createTeam_returnsErrorOnFailure() throws Exception {
        doThrow(new IllegalStateException("Failed")).when(hrService)
            .createTeam(eq("Sales"), eq(null), eq(USER_ID));

        mockMvc.perform(post("/api/hr/teams")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("name", "Sales"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error", is("Failed")));
    }

    @Test
    void appointTeamLead_returnsAppointmentId() throws Exception {
        TeamLeadAppointment appointment = new TeamLeadAppointment();
        appointment.setId(APPOINTMENT_ID);
        when(hrService.appointTeamLead(eq(EMPLOYEE_ID), eq("Growth"), eq(USER_ID)))
            .thenReturn(appointment);

        mockMvc.perform(post("/api/hr/team-leads")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "employeeId", EMPLOYEE_ID.toString(),
                    "teamName", "Growth"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.appointmentId", is(APPOINTMENT_ID.toString())));
    }

    @Test
    void removeTeamLead_returnsSuccess() throws Exception {
        doNothing().when(hrService).removeTeamLead(eq(EMPLOYEE_ID), eq(USER_ID), eq("Rotation"));

        mockMvc.perform(post("/api/hr/team-leads/{employeeId}/remove", EMPLOYEE_ID)
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("reason", "Rotation"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
