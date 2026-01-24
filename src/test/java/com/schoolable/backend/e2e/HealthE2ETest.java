package com.schoolable.backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class HealthE2ETest extends BaseE2ETest {

    @Test
    void healthEndpointReturnsOk() {
        ResponseEntity<Map> response =
                restTemplate.getForEntity("http://localhost:" + port + "/health", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "ok");
    }
}
