package com.schoolable.backend.compliance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/compliance")
public class ComplianceController {
    
    private final ComplianceService complianceService;
    private final ProfileRepository profileRepository;
    
    public ComplianceController(ComplianceService complianceService, ProfileRepository profileRepository) {
        this.complianceService = complianceService;
        this.profileRepository = profileRepository;
    }
    
    // ==================== POLICY MANAGEMENT (Admin) ====================
    
    /**
     * Get all active compliance policies (Admin)
     */
    @GetMapping("/policies")
    public ResponseEntity<?> getAllPolicies(Authentication auth) {
        Profile profile = resolveProfile(auth);

        if (!isAdmin(auth, profile)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        return ResponseEntity.ok(complianceService.getPolicyDetails());
    }
    
    /**
     * Create a new compliance policy (Admin)
     */
    @PostMapping("/policies")
    public ResponseEntity<?> createPolicy(@RequestBody CompliancePolicy policy, Authentication auth) {
        Profile profile = resolveProfile(auth);

        if (!isAdmin(auth, profile)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        try {
            CompliancePolicy created = complianceService.createPolicy(policy, profile.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update a compliance policy (Admin)
     */
    @PutMapping("/policies/{id}")
    public ResponseEntity<?> updatePolicy(@PathVariable UUID id, @RequestBody CompliancePolicy policy, Authentication auth) {
        Profile profile = resolveProfile(auth);

        if (!isAdmin(auth, profile)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        try {
            CompliancePolicy updated = complianceService.updatePolicy(id, policy);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Delete (deactivate) a compliance policy (Admin)
     */
    @DeleteMapping("/policies/{id}")
    public ResponseEntity<?> deletePolicy(@PathVariable UUID id, Authentication auth) {
        Profile profile = resolveProfile(auth);

        if (!isAdmin(auth, profile)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        complianceService.deletePolicy(id);
        return ResponseEntity.ok(Map.of("message", "Policy deleted successfully"));
    }
    
    // ==================== EMPLOYEE COMPLIANCE ITEMS ====================
    
    /**
     * Get compliance items for the current user (Mobile App)
     */
    @GetMapping("/my-items")
    public ResponseEntity<?> getMyComplianceItems(Authentication auth) {
        Profile profile = resolveProfile(auth);
        
        if (profile == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Profile not found"));
        }
        
        List<Map<String, Object>> items = complianceService.getMyComplianceItems(profile.getId());
        return ResponseEntity.ok(items);
    }
    
    /**
     * Submit a compliance item (Mobile App)
     */
    @PostMapping("/my-items/{policyId}/submit")
    public ResponseEntity<?> submitCompliance(
            @PathVariable UUID policyId,
            @RequestBody Map<String, Object> data,
            Authentication auth) {
        Profile profile = resolveProfile(auth);
        
        if (profile == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Profile not found"));
        }
        
        try {
            ComplianceSubmission submission = complianceService.submitCompliance(policyId, profile.getId(), data);
            return ResponseEntity.ok(Map.of(
                    "message", "Submission successful",
                    "status", submission.getStatus(),
                    "submissionId", submission.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Profile resolveProfile(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof UUID uuid) {
            return profileRepository.findById(uuid).orElse(null);
        }
        String principal = auth.getPrincipal().toString();
        try {
            UUID userId = UUID.fromString(principal);
            return profileRepository.findById(userId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return profileRepository.findByEmail(principal).orElse(null);
        }
    }

    private boolean isAdmin(Authentication auth, Profile profile) {
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return true;
        }
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))) {
            return true;
        }
        if (profile == null || profile.getRole() == null) {
            return false;
        }
        String role = profile.getRole().toLowerCase(Locale.ROOT);
        return role.equals("admin") || role.equals("super_admin") || role.equals("superadmin");
    }
    
    // ==================== ADMIN TRACKING ====================
    
    /**
     * Get overall compliance metrics (Admin Dashboard)
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getComplianceMetrics(Authentication auth) {
        String email = auth.getName();
        Profile profile = profileRepository.findByEmail(email).orElse(null);
        
        if (profile == null || !"admin".equalsIgnoreCase(profile.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        return ResponseEntity.ok(complianceService.getComplianceMetrics());
    }
    
    /**
     * Get submissions for a specific policy (Admin)
     */
    @GetMapping("/policies/{policyId}/submissions")
    public ResponseEntity<?> getPolicySubmissions(@PathVariable UUID policyId, Authentication auth) {
        String email = auth.getName();
        Profile profile = profileRepository.findByEmail(email).orElse(null);
        
        if (profile == null || !"admin".equalsIgnoreCase(profile.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        List<ComplianceSubmission> submissions = complianceService.getPolicySubmissions(policyId);
        
        // Enrich with user info
        List<Map<String, Object>> enriched = submissions.stream().map(sub -> {
            Profile user = profileRepository.findById(sub.getUserId()).orElse(null);
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", sub.getId());
            map.put("status", sub.getStatus());
            map.put("submittedAt", sub.getSubmittedAt());
            map.put("fileUrl", sub.getFileUrl());
            map.put("fileName", sub.getFileName());
            map.put("acknowledged", sub.getAcknowledged());
            map.put("reviewNotes", sub.getReviewNotes());
            if (user != null) {
                map.put("userName", user.getFullName());
                map.put("userEmail", user.getEmail());
                map.put("userDepartment", user.getDepartment());
            }
            return map;
        }).toList();
        
        return ResponseEntity.ok(enriched);
    }
    
    /**
     * Review a submission (Admin - approve/reject document uploads)
     */
    @PatchMapping("/submissions/{submissionId}/review")
    public ResponseEntity<?> reviewSubmission(
            @PathVariable UUID submissionId,
            @RequestBody Map<String, String> data,
            Authentication auth) {
        String email = auth.getName();
        Profile profile = profileRepository.findByEmail(email).orElse(null);
        
        if (profile == null || !"admin".equalsIgnoreCase(profile.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        String status = data.get("status"); // approved or rejected
        String notes = data.get("notes");
        
        if (status == null || (!status.equals("approved") && !status.equals("rejected"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status must be 'approved' or 'rejected'"));
        }
        
        try {
            ComplianceSubmission reviewed = complianceService.reviewSubmission(
                    submissionId, profile.getId(), status, notes);
            return ResponseEntity.ok(Map.of(
                    "message", "Submission reviewed",
                    "status", reviewed.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
