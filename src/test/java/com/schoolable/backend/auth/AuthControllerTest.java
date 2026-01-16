package com.schoolable.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.email.ResendEmailService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Password123!";
    private static final String FULL_NAME = "User One";
    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private EmailVerificationTokenRepository tokenRepository;

    @MockBean
    private ResendEmailService emailService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtService jwtService;

    @Test
    void signup_returnsVerificationToken() throws Exception {
        when(profileRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "email", EMAIL,
                        "password", PASSWORD,
                        "fullName", FULL_NAME
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationToken", matchesPattern("\\d{6}")))
            .andExpect(jsonPath("$.message").value("Signup successful. Please verify your email to continue."));

        verify(profileRepository).save(any(Profile.class));
        verify(emailService).sendVerificationEmail(eq(EMAIL), any(String.class));
    }

    @Test
    void signup_rejectsExistingEmail() throws Exception {
        when(profileRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "email", EMAIL,
                        "password", PASSWORD,
                        "fullName", FULL_NAME
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Email already in use"));

        verify(profileRepository, never()).save(any(Profile.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void login_rejectsUnknownEmail() throws Exception {
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_rejectsUnverifiedEmail() throws Exception {
        Profile profile = buildProfile(false);
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(profile));
        when(passwordEncoder.matches(PASSWORD, profile.getPasswordHash())).thenReturn(true);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error")
                .value("Email not verified. Please verify your email first."));
    }

    @Test
    void login_returnsTokenAndProfile() throws Exception {
        Profile profile = buildProfile(true);
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(profile));
        when(passwordEncoder.matches(PASSWORD, profile.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(profile.getId(), profile.getEmail(), profile.getRole()))
            .thenReturn("jwt-token");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.profile.email").value(EMAIL))
            .andExpect(jsonPath("$.profile.full_name").value(FULL_NAME))
            .andExpect(jsonPath("$.profile.role").value("employee"));
    }

    @Test
    void verifyEmail_marksProfileAndToken() throws Exception {
        EmailVerificationToken token = buildToken(PROFILE_ID, OffsetDateTime.now().plusDays(1), "123456");
        Profile profile = buildProfile(false);
        when(tokenRepository.findByTokenAndUsedIsFalse("123456")).thenReturn(Optional.of(token));
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", "123456"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Email verified. You can now log in."));

        verify(profileRepository).save(any(Profile.class));
        verify(tokenRepository).save(token);
    }

    @Test
    void verifyEmail_rejectsInvalidToken() throws Exception {
        when(tokenRepository.findByTokenAndUsedIsFalse("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", "bad-token"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid or already used token"));
    }

    @Test
    void resendVerification_rejectsAlreadyVerified() throws Exception {
        Profile profile = buildProfile(true);
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Email already verified"));
    }

    @Test
    void resendVerification_returnsToken() throws Exception {
        Profile profile = buildProfile(false);
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationToken", matchesPattern("\\d{6}")))
            .andExpect(jsonPath("$.message").value("Verification email re-sent."));

        verify(tokenRepository).deleteByProfileId(PROFILE_ID);
    }

    @Test
    void requestPasswordReset_hidesUnknownAccount() throws Exception {
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message")
                .value("If an account with this email exists, a reset code has been sent."))
            .andExpect(jsonPath("$.resetToken").doesNotExist());

        verifyNoInteractions(emailService);
    }

    @Test
    void requestPasswordReset_returnsToken() throws Exception {
        Profile profile = buildProfile(true);
        when(profileRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resetToken", matchesPattern("\\d{6}")))
            .andExpect(jsonPath("$.message")
                .value("If an account with this email exists, a reset code has been sent."));

        verify(tokenRepository).deleteByProfileId(PROFILE_ID);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), any(String.class));
    }

    @Test
    void verifyResetCode_acceptsValidCode() throws Exception {
        EmailVerificationToken token = buildToken(PROFILE_ID, OffsetDateTime.now().plusHours(2), "654321");
        when(tokenRepository.findByToken("654321")).thenReturn(Optional.of(token));

        mockMvc.perform(post("/auth/verify-reset-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("code", "654321"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.message").value("Code verified. You can now reset your password."));
    }

    @Test
    void completeReset_updatesPasswordAndDeletesToken() throws Exception {
        EmailVerificationToken token = buildToken(PROFILE_ID, OffsetDateTime.now().plusHours(2), "654321");
        Profile profile = buildProfile(true);
        when(tokenRepository.findByToken("654321")).thenReturn(Optional.of(token));
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hash");

        mockMvc.perform(post("/auth/complete-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "code", "654321",
                        "newPassword", "NewPassword1!"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message")
                .value("Password reset successful. You can now login with your new password."));

        verify(profileRepository).save(any(Profile.class));
        verify(tokenRepository).delete(token);
    }

    @Test
    void completeReset_rejectsInvalidCode() throws Exception {
        when(tokenRepository.findByToken("bad-code")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/complete-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "code", "bad-code",
                        "newPassword", "NewPassword1!"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid reset code"));
    }

    private Profile buildProfile(boolean verified) {
        Profile profile = new Profile();
        profile.setId(PROFILE_ID);
        profile.setEmail(EMAIL);
        profile.setFullName(FULL_NAME);
        profile.setRole("employee");
        profile.setPasswordHash("hashed-password");
        profile.setStatus(verified ? "active" : "pending_verification");
        profile.setIsTeamLead(false);
        if (verified) {
            profile.setEmailVerifiedAt(OffsetDateTime.now().minusDays(1));
        }
        return profile;
    }

    private EmailVerificationToken buildToken(UUID profileId, OffsetDateTime expiresAt, String tokenValue) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setProfileId(profileId);
        token.setToken(tokenValue);
        token.setExpiresAt(expiresAt);
        token.setUsed(false);
        return token;
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
