package com.schoolable.backend.auth;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.schoolable.backend.email.ResendEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
public class AuthController {

    private final ProfileRepository profileRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final ResendEmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            ProfileRepository profileRepository,
            EmailVerificationTokenRepository tokenRepository,
            ResendEmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.profileRepository = profileRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }
    @Operation(summary = "Debug token parsing")
@GetMapping("/debug")
public ResponseEntity<?> debugToken(@RequestParam("token") String token) {
    try {
        return ResponseEntity.ok(jwtService.parse(token));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}


    @Operation(summary = "Sign up (requires email verification)")
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest req) {
        if (profileRepository.existsByEmailIgnoreCase(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
        }
        Profile p = new Profile();
        p.setId(UUID.randomUUID());
        p.setEmail(req.email().toLowerCase());
        p.setFullName(req.fullName());
        p.setRole("employee");
        p.setStatus("pending_verification");
        p.setPasswordHash(passwordEncoder.encode(req.password()));
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(p);

        String verificationToken = createVerificationToken(p.getId());
        emailService.sendVerificationEmail(p.getEmail(), verificationToken);

        return ResponseEntity.ok(Map.of(
                "message", "Signup successful. Please verify your email to continue.",
                "verificationToken", verificationToken // Returned for dev/testing; in production send via email.
        ));
    }

    @Operation(summary = "Login (requires verified email)")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        var profileOpt = profileRepository.findByEmailIgnoreCase(req.email());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        var profile = profileOpt.get();
        if (profile.getPasswordHash() == null || !passwordEncoder.matches(req.password(), profile.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        if (profile.getEmailVerifiedAt() == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Email not verified. Please verify your email first."));
        }
        String token = jwtService.generateToken(profile.getId(), profile.getEmail(), profile.getRole());
        
        // Return full profile data so client can check completion status
        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("token", token),
                Map.entry("profile", buildProfileResponse(profile))
        ));
    }

    // Helper to build complete profile response
    private Map<String, Object> buildProfileResponse(Profile p) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", p.getId());
        response.put("email", p.getEmail() != null ? p.getEmail() : "");
        response.put("full_name", p.getFullName() != null ? p.getFullName() : "");
        response.put("role", p.getRole() != null ? p.getRole() : "");
        response.put("department", p.getDepartment() != null ? p.getDepartment() : "");
        response.put("employee_id", p.getEmployeeId() != null ? p.getEmployeeId() : "");
        response.put("phone", p.getPhone() != null ? p.getPhone() : "");
        response.put("status", p.getStatus() != null ? p.getStatus() : "");
        response.put("job_title", p.getJobTitle() != null ? p.getJobTitle() : "");
        response.put("gender", p.getGender() != null ? p.getGender() : "");
        response.put("address", p.getAddress() != null ? p.getAddress() : "");
        response.put("city", p.getCity() != null ? p.getCity() : "");
        response.put("state", p.getState() != null ? p.getState() : "");
        response.put("is_team_lead", p.getIsTeamLead() != null ? p.getIsTeamLead() : false);
        response.put("team_lead_request_status", p.getTeamLeadRequestStatus() != null ? p.getTeamLeadRequestStatus() : "none");
        response.put("team_lead_requested_at", p.getTeamLeadRequestedAt() != null ? p.getTeamLeadRequestedAt().toString() : null);
        if (p.getDateOfBirth() != null) {
            response.put("date_of_birth", p.getDateOfBirth().toString());
        }
        if (p.getDateJoined() != null) {
            response.put("date_joined", p.getDateJoined().toString());
        }
        if (p.getProfileCompletedAt() != null) {
            response.put("profile_completed_at", p.getProfileCompletedAt().toString());
        }
        if (p.getAvatarUrl() != null) {
            response.put("avatar_url", p.getAvatarUrl());
        }
        return response;
    }


    @Operation(summary = "Verify email with token")
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        var tokenOpt = tokenRepository.findByTokenAndUsedIsFalse(req.token());
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or already used token"));
        }

        var token = tokenOpt.get();
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token expired"));
        }

        var profileOpt = profileRepository.findById(token.getProfileId());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        var profile = profileOpt.get();
        profile.setEmailVerifiedAt(OffsetDateTime.now());
        profile.setStatus("active");
        profile.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(profile);

        token.setUsed(true);
        tokenRepository.save(token);

        return ResponseEntity.ok(Map.of("message", "Email verified. You can now log in."));
    }

    @Operation(summary = "Verify email via link (GET)")
    @GetMapping(value = "/verify-link", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmailLink(@RequestParam("token") String token) {
        var tokenOpt = tokenRepository.findByTokenAndUsedIsFalse(token);
        if (tokenOpt.isEmpty()) {
            return htmlResponse("Invalid or already used token.");
        }

        var t = tokenOpt.get();
        if (t.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return htmlResponse("Token expired.");
        }

        var profileOpt = profileRepository.findById(t.getProfileId());
        if (profileOpt.isEmpty()) {
            return htmlResponse("Profile not found.");
        }

        var profile = profileOpt.get();
        profile.setEmailVerifiedAt(OffsetDateTime.now());
        profile.setStatus("active");
        profile.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(profile);

        t.setUsed(true);
        tokenRepository.save(t);

        return htmlResponse("Email verified! You can now close this tab and log in.");
    }

    @Operation(summary = "Resend verification token")
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendRequest req) {
        var profileOpt = profileRepository.findByEmailIgnoreCase(req.email());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Account not found"));
        }
        var profile = profileOpt.get();
        if (profile.getEmailVerifiedAt() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already verified"));
        }

        tokenRepository.deleteByProfileId(profile.getId());
        String token = createVerificationToken(profile.getId());
        return ResponseEntity.ok(Map.of(
                "message", "Verification email re-sent.",
                "verificationToken", token
        ));
    }

    private String createVerificationToken(UUID profileId) {
        var token = new EmailVerificationToken();
        token.setProfileId(profileId);
        token.setToken(generateOtp());
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        tokenRepository.save(token);
        return token.getToken();
    }

    // ==================== PASSWORD RESET ====================

    @Operation(summary = "Request password reset (sends OTP to email)")
    @PostMapping("/reset-password")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody ResetPasswordRequest req) {
        var profileOpt = profileRepository.findByEmailIgnoreCase(req.email());
        if (profileOpt.isEmpty()) {
            // Don't reveal if email exists or not for security
            return ResponseEntity.ok(Map.of(
                "message", "If an account with this email exists, a reset code has been sent."
            ));
        }
        
        var profile = profileOpt.get();
        
        // Create a reset token (reusing the verification token table)
        tokenRepository.deleteByProfileId(profile.getId());
        String resetToken = createVerificationToken(profile.getId());
        
        // Send reset email
        emailService.sendPasswordResetEmail(profile.getEmail(), resetToken);
        
        return ResponseEntity.ok(Map.of(
            "message", "If an account with this email exists, a reset code has been sent.",
            "resetToken", resetToken // For development only - remove in production
        ));
    }

    @Operation(summary = "Verify reset code")
    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest req) {
        var tokenOpt = tokenRepository.findByToken(req.code());
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid reset code"));
        }
        
        var token = tokenOpt.get();
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reset code has expired"));
        }
        
        return ResponseEntity.ok(Map.of(
            "valid", true,
            "message", "Code verified. You can now reset your password."
        ));
    }

    @Operation(summary = "Complete password reset with new password")
    @PostMapping("/complete-reset")
    public ResponseEntity<?> completePasswordReset(@Valid @RequestBody CompleteResetRequest req) {
        var tokenOpt = tokenRepository.findByToken(req.code());
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid reset code"));
        }
        
        var token = tokenOpt.get();
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reset code has expired"));
        }
        
        // Find the profile
        var profileOpt = profileRepository.findById(token.getProfileId());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Account not found"));
        }
        
        // Update password
        var profile = profileOpt.get();
        profile.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        profile.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(profile);
        
        // Delete the used token
        tokenRepository.delete(token);
        
        return ResponseEntity.ok(Map.of(
            "message", "Password reset successful. You can now login with your new password."
        ));
    }

    private ResponseEntity<String> htmlResponse(String message) {
        String html = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Verification</title></head>
                <body style="font-family: Arial, sans-serif; padding: 24px;">
                <h2>WorkSight</h2>
                <p>%s</p>
                </body>
                </html>
                """.formatted(message);
        return ResponseEntity.ok(html);
    }

    private String generateOtp() {
        int code = (int) (Math.random() * 900000) + 100000; // 6-digit
        return String.valueOf(code);
    }

    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            @NotBlank String fullName
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record VerifyEmailRequest(@NotBlank String token) {}

    public record ResendRequest(@Email @NotBlank String email) {}

    public record ResetPasswordRequest(@Email @NotBlank String email) {}

    public record VerifyResetCodeRequest(@NotBlank String code) {}

    public record CompleteResetRequest(
            @NotBlank String code,
            @NotBlank String newPassword
    ) {}
}
