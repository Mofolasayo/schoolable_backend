package com.schoolable.backend.kpi;

import com.schoolable.backend.notification.NotificationService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KpiApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
class KpiApprovalControllerTest {

    private static final UUID USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID KPI_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndividualKpiRepository individualKpiRepository;

    @MockBean
    private TeamKpiRepository teamKpiRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private KpiHistoryRepository kpiHistoryRepository;

    @MockBean
    private NotificationService notificationService;

    @Test
    void getPendingKpis_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/kpi-approval/pending")
                .principal(userAuth()))
            .andExpect(status().isForbidden());
    }

    @Test
    void submitForApproval_returnsNotFound() throws Exception {
        when(individualKpiRepository.findById(KPI_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/kpi-approval/submit/{kpiId}", KPI_ID)
                .principal(userAuth()))
            .andExpect(status().isNotFound());
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }

    @SuppressWarnings("unused")
    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            USER_ID,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
