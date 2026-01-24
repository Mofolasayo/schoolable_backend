package com.schoolable.backend.e2e;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.compliance.CompliancePolicy;
import com.schoolable.backend.compliance.CompliancePolicyRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ComplianceE2ETest extends BaseE2ETest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private CompliancePolicyRepository compliancePolicyRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @BeforeEach
    void cleanup() {
        compliancePolicyRepository.deleteAll();
        profileRepository.deleteAll();
    }

    @Test
    void policiesEndpointReturnsActivePolicies() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setRole("admin");
        profileRepository.save(profile);

        CompliancePolicy policy = new CompliancePolicy();
        policy.setTitle("Data Protection");
        policy.setCategory("Data Security");
        policy.setType("policy");
        policy.setDepartment(null);
        policy.setDeadline(LocalDate.now().plusDays(30));
        compliancePolicyRepository.save(policy);

        var auth = new UsernamePasswordAuthenticationToken(
                USER_ID,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/compliance/policies")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title").value("Data Protection"));
    }
}
