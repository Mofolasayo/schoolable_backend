package com.schoolable.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

@WebMvcTest(AiJobController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiJobControllerTest {

    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiJobRepository aiJobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getJob_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/ai/jobs/{id}", JOB_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthenticated")));
    }

    @Test
    void getJob_returnsNotFound() throws Exception {
        when(aiJobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/ai/jobs/{id}", JOB_ID)
                .principal(auth(USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", is("Job not found")));
    }

    @Test
    void getJob_rejectsNonOwner() throws Exception {
        AiJob job = new AiJob();
        job.setId(JOB_ID);
        job.setJobType("weekly_insight");
        job.setPayload(objectMapper.readTree("{\"requestedBy\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"}"));
        when(aiJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/ai/jobs/{id}", JOB_ID)
                .principal(auth(USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    void getJob_allowsAdmin() throws Exception {
        AiJob job = new AiJob();
        job.setId(JOB_ID);
        job.setJobType("weekly_insight");
        job.setPayload(objectMapper.readTree("{\"requestedBy\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"}"));
        when(aiJobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/ai/jobs/{id}", JOB_ID)
                .principal(admin(USER_ID))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(JOB_ID.toString())))
            .andExpect(jsonPath("$.jobType", is("weekly_insight")));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }

    private UsernamePasswordAuthenticationToken admin(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
