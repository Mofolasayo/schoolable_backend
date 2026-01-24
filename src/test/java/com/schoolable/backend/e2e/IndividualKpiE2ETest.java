package com.schoolable.backend.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class IndividualKpiE2ETest extends BaseE2ETest {

    @Test
    void myKpisReturnsDefaults() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(get("/api/individual-kpis/my")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis").isArray())
            .andExpect(jsonPath("$.totalWeight").value(0));
    }
}
