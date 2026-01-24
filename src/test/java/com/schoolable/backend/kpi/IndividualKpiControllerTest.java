package com.schoolable.backend.kpi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.Profile;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IndividualKpiController.class)
@AutoConfigureMockMvc(addFilters = false)
class IndividualKpiControllerTest {

    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IndividualKpiRepository individualKpiRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private KpiLockService kpiLockService;

    @MockBean
    private KpiChangeRequestRepository changeRequestRepository;

    @Test
    void getTeamKpis_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/individual-kpis/my-team"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthenticated")));
    }

    @Test
    void createKpi_rejectsNonTeamLead() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setIsTeamLead(false);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/api/individual-kpis")
                .principal(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", is("Only team leads can create individual KPIs")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
