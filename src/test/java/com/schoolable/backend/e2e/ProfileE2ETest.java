package com.schoolable.backend.e2e;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ProfileE2ETest extends BaseE2ETest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TEAMMATE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private ProfileRepository profileRepository;

    @BeforeEach
    void cleanup() {
        profileRepository.deleteAll();
    }

    @Test
    void profileEndpointsReturnExpectedData() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setFullName("Alex Doe");
        profile.setEmail("alex@example.com");
        profile.setDepartment("Engineering");
        profile.setRole("employee");
        profileRepository.save(profile);

        Profile teammate = new Profile();
        teammate.setId(TEAMMATE_ID);
        teammate.setFullName("Jamie Doe");
        teammate.setDepartment("Engineering");
        teammate.setRole("employee");
        profileRepository.save(teammate);

        var auth = new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());

        mockMvc.perform(get("/profile/me")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.full_name").value("Alex Doe"))
            .andExpect(jsonPath("$.department").value("Engineering"));

        mockMvc.perform(get("/profile/is-complete")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.is_complete").value(false))
            .andExpect(jsonPath("$.email").value("alex@example.com"));

        mockMvc.perform(get("/profile/team")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].full_name").value("Jamie Doe"));

        mockMvc.perform(get("/profile/departments")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.departments").isArray())
            .andExpect(jsonPath("$.departments", hasItem("Engineering")));
    }
}
