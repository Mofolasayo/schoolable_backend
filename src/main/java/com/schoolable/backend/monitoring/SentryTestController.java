package com.schoolable.backend.monitoring;

import io.sentry.Sentry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SentryTestController {

    @Value("${sentry.test-token:}")
    private String testToken;

    @GetMapping("/internal/sentry-test")
    public ResponseEntity<?> trigger(
            @RequestHeader(value = "X-Sentry-Test-Token", required = false) String token
    ) {
        if (testToken == null || testToken.isBlank() || token == null || !testToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Sentry.captureException(new RuntimeException("Sentry test error"));

        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}
