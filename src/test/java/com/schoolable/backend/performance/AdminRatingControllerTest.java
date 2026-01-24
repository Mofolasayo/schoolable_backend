package com.schoolable.backend.performance;

import com.schoolable.backend.hr.TeamLeadAppointment;
import com.schoolable.backend.hr.TeamLeadRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRatingController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminRatingControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID TEAM_LEAD_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminTeamLeadRatingRepository ratingRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private TeamLeadRepository teamLeadRepository;

    @MockBean
    private AuraTrendAlertRepository alertRepository;

    @Test
    void getTeamLeadsForRating_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ratings/team-leads"))
            .andExpect(status().isForbidden());
    }

    @Test
    void getTeamLeadsForRating_returnsTeamLeads() throws Exception {
        Profile admin = new Profile();
        admin.setId(ADMIN_ID);
        admin.setRole("admin");
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        Profile lead = new Profile();
        lead.setId(TEAM_LEAD_ID);
        lead.setFullName("Lead One");
        lead.setDepartment("Sales");
        lead.setIsTeamLead(true);
        when(profileRepository.findByIsTeamLeadTrue()).thenReturn(List.of(lead));
        when(teamLeadRepository.findActiveTeamLeads()).thenReturn(List.of());
        when(ratingRepository.existsByTeamLeadIdAndWeekNumberAndYear(TEAM_LEAD_ID, 1, 2026))
            .thenReturn(false);
        when(ratingRepository.findLatestByTeamLeadId(TEAM_LEAD_ID)).thenReturn(Optional.empty());
        when(profileRepository.countByTeamLeadId(TEAM_LEAD_ID)).thenReturn(2L);

        mockMvc.perform(get("/api/admin/ratings/team-leads")
                .principal(adminAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.teamLeads[0].name", is("Lead One")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            ADMIN_ID,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
