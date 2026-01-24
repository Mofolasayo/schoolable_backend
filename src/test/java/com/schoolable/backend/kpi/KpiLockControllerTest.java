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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KpiLockController.class)
@AutoConfigureMockMvc(addFilters = false)
class KpiLockControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LOCK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KpiPeriodLockRepository lockRepository;

    @Test
    void createLock_requiresAdmin() throws Exception {
        mockMvc.perform(post("/api/kpi/locks")
                .principal(userAuth())
                .contentType("application/json")
                .content("{\"kpiType\":\"team\",\"quarter\":\"Q1\",\"year\":2026}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unlock_returnsNotFound() throws Exception {
        when(lockRepository.findById(LOCK_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/kpi/locks/{id}/unlock", LOCK_ID)
                .principal(adminAuth()))
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
