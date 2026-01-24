package com.schoolable.backend.performance;

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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuraScoreJobController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuraScoreJobControllerTest {

    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID JOB_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuraScoreJobRepository jobRepository;

    @Test
    void getJob_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/performance/aura-jobs/{id}", JOB_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getJob_blocksOtherUsers() throws Exception {
        AuraScoreJob job = new AuraScoreJob();
        job.setId(JOB_ID);
        job.setJobType("weekly");
        job.setRequestedBy(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/performance/aura-jobs/{id}", JOB_ID)
                .principal(userAuth()))
            .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
