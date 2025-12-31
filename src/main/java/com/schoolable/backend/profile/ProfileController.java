package com.schoolable.backend.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.schoolable.backend.storage.StorageService;

import java.sql.Date;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
@Tag(name = "Profile")
public class ProfileController {

    private final ProfileRepository profileRepository;
    private final StorageService storageService;

    public ProfileController(ProfileRepository profileRepository, StorageService storageService) {
        this.profileRepository = profileRepository;
        this.storageService = storageService;
    }

    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }
        var p = profileOpt.get();
        return ResponseEntity.ok(buildProfileResponse(p));
    }

    @Operation(summary = "Check if current user's profile is complete")
    @GetMapping("/is-complete")
    public ResponseEntity<?> isProfileComplete(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }
        var p = profileOpt.get();
        boolean isComplete = p.getProfileCompletedAt() != null;
        return ResponseEntity.ok(Map.of(
            "is_complete", isComplete,
            "profile_completed_at", isComplete ? p.getProfileCompletedAt().toString() : null,
            "email", p.getEmail() != null ? p.getEmail() : "",
            "full_name", p.getFullName() != null ? p.getFullName() : ""
        ));
    }

    @Operation(summary = "List all profiles")
    @GetMapping("/all")
    public ResponseEntity<?> all(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        List<Map<String, Object>> result = profileRepository.findAll()
                .stream()
                .map(this::buildProfileResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "List staff profiles (non-admin)")
    @GetMapping("/staff")
    public ResponseEntity<?> staff(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        // Get all profiles except admins
        List<Map<String, Object>> result = profileRepository.findAll()
                .stream()
                .filter(p -> !"admin".equalsIgnoreCase(p.getRole()))
                .map(this::buildProfileResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get team members in the same department")
    @GetMapping("/team")
    public ResponseEntity<?> getMyTeam(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }
        
        String department = profileOpt.get().getDepartment();
        if (department == null || department.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User does not belong to a department"));
        }

        List<Map<String, Object>> result = profileRepository.findByDepartment(department)
                .stream()
                .filter(p -> !p.getId().equals(userId)) // Exclude self
                .map(this::buildProfileResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Complete profile after login")
    @PostMapping("/complete")
    public ResponseEntity<?> completeProfile(Authentication auth, @RequestBody CompleteProfileRequest req) {
        System.out.println("📋 completeProfile endpoint called");
        System.out.println("   Request body: " + req);
        System.out.println("   Authentication object: " + (auth != null ? "Present" : "NULL"));
        if (auth != null) {
            System.out.println("   Principal: " + auth.getPrincipal());
            System.out.println("   Authorities: " + auth.getAuthorities());
        }
        
        try {
            if (auth == null || auth.getPrincipal() == null) {
                System.out.println("   ❌ Returning 401: missing authentication");
                return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
            }
            UUID userId = (UUID) auth.getPrincipal();
            System.out.println("   ✅ User ID: " + userId);
            
            var profileOpt = profileRepository.findById(userId);
            if (profileOpt.isEmpty()) {
                System.out.println("   ❌ Profile not found for userId: " + userId);
                return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
            }
            var p = profileOpt.get();

            p.setEmployeeId(req.employeeId());
            p.setPhone(req.phone());
            p.setDepartment(req.department());
            // The request's 'role' field is actually the user's job title (e.g., "Product Manager")
            // Save it to the new job_title column, not the role column (which is permission level)
            if (req.role() != null && !req.role().isEmpty()) {
                p.setJobTitle(req.role());
            }
            if (req.dateJoined() != null && !req.dateJoined().isEmpty()) {
                System.out.println("   Parsing dateJoined: " + req.dateJoined());
                p.setDateJoined(parseFlexibleDateTime(req.dateJoined()));
            }
            p.setGender(req.gender());
            if (req.dateOfBirth() != null && !req.dateOfBirth().isEmpty()) {
                System.out.println("   Parsing dateOfBirth: " + req.dateOfBirth());
                p.setDateOfBirth(Date.valueOf(req.dateOfBirth()));
            }
            p.setAddress(req.address());
            p.setCity(req.city());
            p.setState(req.state());
            
            if (req.isTeamLead() != null) {
                p.setIsTeamLead(req.isTeamLead());
            }
            if (req.employeeLevel() != null) {
                p.setEmployeeLevel(req.employeeLevel());
            }

            p.setStatus("active");
            p.setProfileCompletedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());

            profileRepository.save(p);
            System.out.println("   ✅ Profile saved successfully");

            return ResponseEntity.ok(buildProfileResponse(p));
        } catch (Exception e) {
            System.out.println("   ❌ ERROR in completeProfile: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to complete profile: " + e.getMessage()));
        }
    }

    @Operation(summary = "Update profile details")
    @PostMapping("/update")
    public ResponseEntity<?> updateProfile(Authentication auth, @RequestBody UpdateProfileRequest req) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }
        var p = profileOpt.get();

        if (req.fullName() != null) p.setFullName(req.fullName());
        if (req.jobTitle() != null) p.setJobTitle(req.jobTitle());
        if (req.phone() != null) p.setPhone(req.phone());
        if (req.address() != null) p.setAddress(req.address());
        if (req.city() != null) p.setCity(req.city());
        if (req.state() != null) p.setState(req.state());

        p.setUpdatedAt(OffsetDateTime.now());
        profileRepository.save(p);

        return ResponseEntity.ok(buildProfileResponse(p));
    }

    @Operation(summary = "Upload profile avatar")
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(Authentication auth, @RequestParam("avatar") MultipartFile file) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal(); // This cast assumes principal is UUID
        
        try {
            // Upload file using StorageService
            // Use a folder like "avatars/{userId}"
            Map<String, Object> uploadResult = storageService.uploadFile(file, "avatars/" + userId);
            String fileUrl = (String) uploadResult.get("url");
            
            var profileOpt = profileRepository.findById(userId);
            if (profileOpt.isEmpty()) {
                 return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
            }
            var p = profileOpt.get();
            p.setAvatarUrl(fileUrl);
            p.setUpdatedAt(OffsetDateTime.now());
            profileRepository.save(p);
            
            return ResponseEntity.ok(Map.of("message", "Avatar updated", "avatar_url", fileUrl));
            
        } catch (Exception e) {
             return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload avatar: " + e.getMessage()));
        }
    }

    /**
     * Build a null-safe profile response map.
     * Using HashMap instead of Map.of() because Map.of() throws NPE for null values.
     * Auto-generates avatar_url using DiceBear if not set.
     */
    private Map<String, Object> buildProfileResponse(Profile p) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", p.getId());
        response.put("email", p.getEmail());
        response.put("full_name", p.getFullName());
        response.put("role", p.getRole());
        response.put("job_title", p.getJobTitle());
        response.put("department", p.getDepartment());
        response.put("status", p.getStatus());
        
        // Auto-generate avatar_url if not set
        String avatarUrl = p.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            String style = "bottts"; // default
            String gender = p.getGender();
            if (gender != null) {
                if (gender.equalsIgnoreCase("male")) {
                    style = "adventurer";
                } else if (gender.equalsIgnoreCase("female")) {
                    style = "adventurer-neutral";
                }
            }
            // Use employee_id, email, or full_name as seed
            String seed = p.getEmployeeId();
            if (seed == null || seed.isEmpty()) seed = p.getEmail();
            if (seed == null || seed.isEmpty()) seed = p.getFullName();
            if (seed == null || seed.isEmpty()) seed = "User";
            
            avatarUrl = "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
        }
        response.put("avatar_url", avatarUrl);
        
        response.put("employee_id", p.getEmployeeId());
        response.put("phone", p.getPhone());
        response.put("gender", p.getGender());
        response.put("date_of_birth", p.getDateOfBirth());
        response.put("address", p.getAddress());
        response.put("city", p.getCity());
        response.put("state", p.getState());
        response.put("date_joined", p.getDateJoined());
        response.put("created_at", p.getCreatedAt());
        response.put("updated_at", p.getUpdatedAt());
        response.put("email_verified_at", p.getEmailVerifiedAt());
        response.put("profile_completed_at", p.getProfileCompletedAt());
        return response;
    }

    /**
     * Parse a date-time string flexibly, handling various ISO formats.
     * Supports: 2025-10-01T00:00:00.000, 2025-10-01T00:00:00.000Z, 2025-10-01T00:00:00+00:00
     */
    private OffsetDateTime parseFlexibleDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        
        // Try parsing as OffsetDateTime first (has timezone info)
        try {
            return OffsetDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException ignored) {
            // Fall through to try other formats
        }
        
        // Try parsing as LocalDateTime and add UTC offset
        try {
            // Handle format like "2025-10-01T00:00:00.000"
            LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr, 
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][.SS][.S]"));
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // Fall through to try other formats
        }
        
        // Try parsing just a date (yyyy-MM-dd) and add time
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr + "T00:00:00",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Unable to parse date: " + dateTimeStr, e);
        }
    }

    public record CompleteProfileRequest(
            String employeeId,
            String phone,
            String department,
            String role,
            String dateJoined, // ISO-8601 string
            String gender,
            String dateOfBirth, // yyyy-MM-dd
            String address,
            String city,
            String state,
            Boolean isTeamLead,
            Integer employeeLevel
    ) {}

    public record UpdateProfileRequest(
        String fullName,
        String jobTitle,
        String phone,
        String address,
        String city,
        String state
    ) {}
}
