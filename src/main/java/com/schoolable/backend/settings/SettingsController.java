package com.schoolable.backend.settings;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ProfileRepository profileRepository;

    public SettingsController(
            OrganizationSettingsRepository organizationSettingsRepository,
            UserPreferenceRepository userPreferenceRepository,
            ProfileRepository profileRepository) {
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping("/organization")
    public ResponseEntity<?> getOrganizationSettings(Authentication auth) {
        Profile profile = requireAdmin(auth);
        if (profile == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        OrganizationSettings settings = organizationSettingsRepository
                .findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    OrganizationSettings created = new OrganizationSettings();
                    created.setUpdatedBy(profile.getId());
                    return organizationSettingsRepository.save(created);
                });

        return ResponseEntity.ok(toOrganizationResponse(settings));
    }

    @PutMapping("/organization")
    public ResponseEntity<?> updateOrganizationSettings(Authentication auth, @RequestBody OrganizationSettingsRequest request) {
        Profile profile = requireAdmin(auth);
        if (profile == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        OrganizationSettings settings = organizationSettingsRepository
                .findFirstByOrderByIdAsc()
                .orElseGet(OrganizationSettings::new);

        if (request.name() != null) settings.setName(request.name());
        if (request.email() != null) settings.setEmail(request.email());
        if (request.license() != null) settings.setLicense(request.license());
        if (request.address() != null) settings.setAddress(request.address());

        settings.setUpdatedBy(profile.getId());
        settings = organizationSettingsRepository.save(settings);

        return ResponseEntity.ok(toOrganizationResponse(settings));
    }

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences(Authentication auth) {
        UUID userId = resolveUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UserPreference pref = userPreferenceRepository.findById(userId)
                .orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setUserId(userId);
                    created.setEmailNotifications(true);
                    created.setPushNotifications(false);
                    created.setMarketingNotifications(false);
                    created.setSecurityAlerts(true);
                    created.setTheme("system");
                    return userPreferenceRepository.save(created);
                });

        return ResponseEntity.ok(toPreferenceResponse(pref));
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreferences(Authentication auth, @RequestBody UserPreferenceRequest request) {
        UUID userId = resolveUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UserPreference pref = userPreferenceRepository.findById(userId)
                .orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setUserId(userId);
                    created.setEmailNotifications(true);
                    created.setPushNotifications(false);
                    created.setMarketingNotifications(false);
                    created.setSecurityAlerts(true);
                    created.setTheme("system");
                    return created;
                });

        if (request.emailNotifications() != null) pref.setEmailNotifications(request.emailNotifications());
        if (request.pushNotifications() != null) pref.setPushNotifications(request.pushNotifications());
        if (request.marketingNotifications() != null) pref.setMarketingNotifications(request.marketingNotifications());
        if (request.securityAlerts() != null) pref.setSecurityAlerts(request.securityAlerts());
        if (request.theme() != null && isValidTheme(request.theme())) {
            pref.setTheme(request.theme());
        }

        pref = userPreferenceRepository.save(pref);
        return ResponseEntity.ok(toPreferenceResponse(pref));
    }

    private boolean isValidTheme(String theme) {
        String normalized = theme.trim().toLowerCase();
        return normalized.equals("light") || normalized.equals("dark") || normalized.equals("system");
    }

    private Map<String, Object> toOrganizationResponse(OrganizationSettings settings) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", settings.getId());
        response.put("name", settings.getName());
        response.put("email", settings.getEmail());
        response.put("license", settings.getLicense());
        response.put("address", settings.getAddress());
        response.put("updatedAt", settings.getUpdatedAt());
        response.put("updatedBy", settings.getUpdatedBy());
        return response;
    }

    private Map<String, Object> toPreferenceResponse(UserPreference pref) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", pref.getUserId());
        response.put("emailNotifications", pref.getEmailNotifications());
        response.put("pushNotifications", pref.getPushNotifications());
        response.put("marketingNotifications", pref.getMarketingNotifications());
        response.put("securityAlerts", pref.getSecurityAlerts());
        response.put("theme", pref.getTheme());
        response.put("updatedAt", pref.getUpdatedAt());
        return response;
    }

    private UUID resolveUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        return UUID.fromString(principal.toString());
    }

    private Profile requireAdmin(Authentication auth) {
        UUID userId = resolveUserId(auth);
        if (userId == null) {
            return null;
        }
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null) {
            return null;
        }
        String role = profile.getRole() != null ? profile.getRole().toLowerCase() : "";
        if (role.equals("admin") || role.equals("super_admin") || role.equals("superadmin")) {
            return profile;
        }
        return null;
    }

    public record OrganizationSettingsRequest(String name, String email, String license, String address) {}

    public record UserPreferenceRequest(
            Boolean emailNotifications,
            Boolean pushNotifications,
            Boolean marketingNotifications,
            Boolean securityAlerts,
            String theme
    ) {}
}
