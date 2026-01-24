package com.schoolable.backend.e2e;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.attendance.OfficeLocationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AttendanceE2ETest extends BaseE2ETest {

    @Autowired
    private OfficeLocationRepository officeLocationRepository;

    @BeforeEach
    void cleanup() {
        officeLocationRepository.deleteAll();
    }

    @Test
    void officesEndpointReturnsEmptyList() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(get("/attendance/offices")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void checkInRequiresDeviceId() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(post("/attendance/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authentication(auth)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }
}
