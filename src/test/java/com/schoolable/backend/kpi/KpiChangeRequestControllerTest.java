package com.schoolable.backend.kpi;

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

@WebMvcTest(KpiChangeRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class KpiChangeRequestControllerTest {

    private static final UUID USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID REQUEST_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KpiChangeRequestRepository changeRequestRepository;

    @MockBean
    private TeamKpiRepository teamKpiRepository;

    @MockBean
    private IndividualKpiRepository individualKpiRepository;

    @MockBean
    private KpiHistoryRepository kpiHistoryRepository;

    @Test
    void list_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/kpi/change-requests")
                .principal(userAuth()))
            .andExpect(status().isForbidden());
    }

    @Test
    void reject_returnsNotFound() throws Exception {
        when(changeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/kpi/change-requests/{id}/reject", REQUEST_ID)
                .principal(adminAuth())
                .contentType("application/json")
                .content("{\"reason\":\"Not needed\"}"))
            .andExpect(status().isNotFound());
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
            USER_ID,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
