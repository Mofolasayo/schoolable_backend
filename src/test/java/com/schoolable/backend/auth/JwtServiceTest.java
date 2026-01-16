package com.schoolable.backend.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef01234567";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setExpirationSeconds(3600);
        jwtService = new JwtService(props);
    }

    @Test
    void generateToken_includesSubjectAndClaims() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", "admin");

        Claims claims = jwtService.parse(token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("user@example.com", claims.get("email", String.class));
        assertEquals("admin", claims.get("role", String.class));
    }

    @Test
    void extractUserId_returnsUuidForValidToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", "employee");

        UUID extracted = jwtService.extractUserId(token);

        assertEquals(userId, extracted);
    }

    @Test
    void extractUserId_returnsNullForInvalidToken() {
        UUID extracted = jwtService.extractUserId("not-a-token");

        assertNull(extracted);
    }

    @Test
    void isExpired_returnsFalseForValidToken() {
        String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", "employee");

        assertFalse(jwtService.isExpired(token));
    }

    @Test
    void isExpired_returnsTrueForExpiredToken() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setExpirationSeconds(-60);
        JwtService expiredService = new JwtService(props);

        String token = expiredService.generateToken(UUID.randomUUID(), "user@example.com", "employee");

        assertTrue(expiredService.isExpired(token));
    }
}
