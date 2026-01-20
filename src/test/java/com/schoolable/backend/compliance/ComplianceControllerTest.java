package com.schoolable.backend.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComplianceController.class)
@AutoConfigureMockMvc(addFilters = false)
class ComplianceControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POLICY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUBMISSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ComplianceService complianceService;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void getAllPolicies_requiresAdmin() throws Exception {
        Profile profile = buildProfile("employee");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/compliance/policies")
                .principal(auth(USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Admin access required"));

        verifyNoInteractions(complianceService);
    }

    @Test
    void getAllPolicies_returnsPolicyDetailsForAdmin() throws Exception {
        Profile profile = buildProfile("admin");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(complianceService.getPolicyDetails())
                .thenReturn(List.of(Map.of(
                        "id", POLICY_ID,
                        "title", "Data Protection"
                )));

        mockMvc.perform(get("/compliance/policies")
                .principal(auth(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(POLICY_ID.toString()))
            .andExpect(jsonPath("$[0].title").value("Data Protection"));
    }

    @Test
    void createPolicy_callsServiceWithProfileId() throws Exception {
        Profile profile = buildProfile("admin");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        CompliancePolicy created = new CompliancePolicy();
        created.setId(POLICY_ID);
        created.setTitle("Data Protection");
        when(complianceService.createPolicy(any(CompliancePolicy.class), eq(USER_ID)))
                .thenReturn(created);

        mockMvc.perform(post("/compliance/policies")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "title", "Data Protection",
                        "category", "General",
                        "type", "policy"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(POLICY_ID.toString()))
            .andExpect(jsonPath("$.title").value("Data Protection"));

        ArgumentCaptor<CompliancePolicy> captor = ArgumentCaptor.forClass(CompliancePolicy.class);
        verify(complianceService).createPolicy(captor.capture(), eq(USER_ID));
        assertEquals("Data Protection", captor.getValue().getTitle());
    }

    @Test
    void submitCompliance_returnsResponse() throws Exception {
        Profile profile = buildProfile("employee");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        ComplianceSubmission submission = new ComplianceSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStatus("submitted");
        when(complianceService.submitCompliance(eq(POLICY_ID), eq(USER_ID), any(Map.class)))
                .thenReturn(submission);

        mockMvc.perform(post("/compliance/my-items/{policyId}/submit", POLICY_ID)
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "type", "upload",
                        "fileUrl", "https://example.com/policy.pdf",
                        "fileName", "policy.pdf"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("submitted"))
            .andExpect(jsonPath("$.submissionId").value(SUBMISSION_ID.toString()))
            .andExpect(jsonPath("$.policyId").value(POLICY_ID.toString()));
    }

    @Test
    void reviewSubmission_rejectsInvalidStatus() throws Exception {
        Profile profile = buildProfile("admin");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(patch("/compliance/submissions/{submissionId}/review", SUBMISSION_ID)
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("status", "pending"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Status must be 'approved' or 'rejected'"));

        verify(complianceService, never())
                .reviewSubmission(eq(SUBMISSION_ID), eq(USER_ID), any(String.class), any(String.class));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }

    private Profile buildProfile(String role) {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setRole(role);
        profile.setDepartment("HR");
        profile.setIsTeamLead(false);
        return profile;
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
