package com.schoolable.backend.skills;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skills Controller
 * Manages skills matrix - organization skills and employee proficiencies.
 */
@RestController
@RequestMapping("/skills")
@Tag(name = "Skills Matrix", description = "Skills management and employee proficiencies")
public class SkillController {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private EmployeeSkillRepository employeeSkillRepository;

    @Autowired
    private ProfileRepository profileRepository;

    private static final List<String> SKILL_CATEGORIES = List.of(
        "technical", "soft", "domain", "tool", "language"
    );

    /**
     * Get all available skills
     */
    @GetMapping
    @Operation(summary = "Get all skills", description = "List available skills in the organization")
    public ResponseEntity<?> getAllSkills(
            @RequestParam(required = false) String category,
            Authentication auth) {
        
        List<Skill> skills;
        if (category != null && !category.isEmpty()) {
            skills = skillRepository.findByCategoryAndIsActiveTrueOrderByNameAsc(category);
        } else {
            skills = skillRepository.findByIsActiveTrueOrderByNameAsc();
        }

        return ResponseEntity.ok(Map.of(
            "skills", skills.stream().map(this::skillToMap).collect(Collectors.toList()),
            "categories", SKILL_CATEGORIES
        ));
    }

    /**
     * Create a new skill (admin only)
     */
    @PostMapping
    @Operation(summary = "Create skill", description = "Add a new skill to the organization")
    public ResponseEntity<?> createSkill(@RequestBody SkillRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        Profile profile = profileRepository.findById(userId).orElse(null);

        if (req.name == null || req.category == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name and category are required"));
        }

        if (!SKILL_CATEGORIES.contains(req.category.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid category",
                "validCategories", SKILL_CATEGORIES
            ));
        }

        String org = profile != null ? profile.getDepartment() : null;
        
        if (skillRepository.existsByNameAndOrganization(req.name, org)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Skill already exists"));
        }

        Skill skill = new Skill();
        skill.setName(req.name);
        skill.setDescription(req.description);
        skill.setCategory(req.category.toLowerCase());
        skill.setOrganization(org);
        skill.setCreatedBy(userId);

        skillRepository.save(skill);

        return ResponseEntity.ok(skillToMap(skill));
    }

    /**
     * Get my skills profile
     */
    @GetMapping("/me")
    @Operation(summary = "Get my skills", description = "Get your own skill proficiencies")
    public ResponseEntity<?> getMySkills(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(getEmployeeSkillsData(userId));
    }

    /**
     * Get employee's skills
     */
    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get employee skills", description = "Get an employee's skill proficiencies")
    public ResponseEntity<?> getEmployeeSkills(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(getEmployeeSkillsData(employeeId));
    }

    /**
     * Add or update my skill
     */
    @PostMapping("/me")
    @Operation(summary = "Add/update my skill", description = "Add or update your skill proficiency")
    public ResponseEntity<?> addOrUpdateMySkill(@RequestBody EmployeeSkillRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());

        if (req.skillId == null || req.proficiencyLevel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "skillId and proficiencyLevel are required"));
        }

        if (req.proficiencyLevel < 1 || req.proficiencyLevel > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "proficiencyLevel must be 1-5"));
        }

        Skill skill = skillRepository.findById(req.skillId).orElse(null);
        if (skill == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Skill not found"));
        }

        EmployeeSkill es = employeeSkillRepository.findByEmployeeIdAndSkillId(userId, req.skillId)
            .orElse(new EmployeeSkill(userId, req.skillId, req.proficiencyLevel));

        es.setProficiencyLevel(req.proficiencyLevel);
        es.setIsSelfAssessed(true);
        es.setYearsExperience(req.yearsExperience);
        es.setNotes(req.notes);
        es.setLastUsedAt(req.lastUsedAt);

        employeeSkillRepository.save(es);

        return ResponseEntity.ok(employeeSkillToMap(es, skill, null));
    }

    /**
     * Verify an employee's skill (manager only)
     */
    @PostMapping("/verify/{employeeSkillId}")
    @Operation(summary = "Verify skill", description = "Verify an employee's skill proficiency")
    public ResponseEntity<?> verifySkill(@PathVariable UUID employeeSkillId, Authentication auth) {
        UUID managerId = UUID.fromString(auth.getName());

        EmployeeSkill es = employeeSkillRepository.findById(employeeSkillId).orElse(null);
        if (es == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Employee skill not found"));
        }

        es.setVerifiedBy(managerId);
        es.setVerifiedAt(LocalDateTime.now());
        es.setIsSelfAssessed(false);

        employeeSkillRepository.save(es);

        Skill skill = skillRepository.findById(es.getSkillId()).orElse(null);
        Profile verifier = profileRepository.findById(managerId).orElse(null);

        return ResponseEntity.ok(employeeSkillToMap(es, skill, verifier));
    }

    /**
     * Find experts for a skill
     */
    @GetMapping("/{skillId}/experts")
    @Operation(summary = "Find skill experts", description = "Find employees with high proficiency in a skill")
    public ResponseEntity<?> findExperts(
            @PathVariable UUID skillId,
            @RequestParam(defaultValue = "3") int minLevel) {
        
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Skill not found"));
        }

        List<EmployeeSkill> experts = employeeSkillRepository.findExpertsForSkill(skillId, minLevel);

        List<Map<String, Object>> expertList = experts.stream()
            .map(es -> {
                Profile profile = profileRepository.findById(es.getEmployeeId()).orElse(null);
                Map<String, Object> expert = new LinkedHashMap<>();
                expert.put("employeeId", es.getEmployeeId().toString());
                expert.put("name", profile != null ? profile.getFullName() : "Unknown");
                expert.put("department", profile != null ? profile.getDepartment() : "");
                expert.put("proficiencyLevel", es.getProficiencyLevel());
                expert.put("proficiencyLabel", es.getProficiencyLabel());
                expert.put("isVerified", es.isVerified());
                expert.put("yearsExperience", es.getYearsExperience());
                return expert;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "skill", skillToMap(skill),
            "experts", expertList,
            "minProficiencyLevel", minLevel
        ));
    }

    /**
     * Delete my skill
     */
    @DeleteMapping("/me/{skillId}")
    @Operation(summary = "Remove my skill")
    public ResponseEntity<?> removeMySkill(@PathVariable UUID skillId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        
        EmployeeSkill es = employeeSkillRepository.findByEmployeeIdAndSkillId(userId, skillId).orElse(null);
        if (es == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Skill not found in your profile"));
        }

        employeeSkillRepository.delete(es);
        return ResponseEntity.ok(Map.of("message", "Skill removed"));
    }

    // Helper methods
    private Map<String, Object> getEmployeeSkillsData(UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId).orElse(null);
        List<EmployeeSkill> employeeSkills = employeeSkillRepository.findByEmployeeIdOrderByProficiencyLevelDesc(employeeId);

        List<Map<String, Object>> skills = employeeSkills.stream()
            .map(es -> {
                Skill skill = skillRepository.findById(es.getSkillId()).orElse(null);
                Profile verifier = es.getVerifiedBy() != null ? 
                    profileRepository.findById(es.getVerifiedBy()).orElse(null) : null;
                return employeeSkillToMap(es, skill, verifier);
            })
            .collect(Collectors.toList());

        return Map.of(
            "employeeId", employeeId.toString(),
            "employeeName", profile != null ? profile.getFullName() : "Unknown",
            "skills", skills,
            "skillCount", skills.size()
        );
    }

    private Map<String, Object> skillToMap(Skill skill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", skill.getId().toString());
        map.put("name", skill.getName());
        map.put("description", skill.getDescription());
        map.put("category", skill.getCategory());
        return map;
    }

    private Map<String, Object> employeeSkillToMap(EmployeeSkill es, Skill skill, Profile verifier) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", es.getId().toString());
        map.put("skillId", es.getSkillId().toString());
        map.put("skillName", skill != null ? skill.getName() : "Unknown");
        map.put("category", skill != null ? skill.getCategory() : "");
        map.put("proficiencyLevel", es.getProficiencyLevel());
        map.put("proficiencyLabel", es.getProficiencyLabel());
        map.put("isSelfAssessed", es.getIsSelfAssessed());
        map.put("isVerified", es.isVerified());
        if (verifier != null) {
            map.put("verifiedBy", verifier.getFullName());
        }
        map.put("yearsExperience", es.getYearsExperience());
        map.put("notes", es.getNotes());
        map.put("updatedAt", es.getUpdatedAt().toString());
        return map;
    }

    // Request DTOs
    public static class SkillRequest {
        public String name;
        public String description;
        public String category;
    }

    public static class EmployeeSkillRequest {
        public UUID skillId;
        public Integer proficiencyLevel;
        public Double yearsExperience;
        public String notes;
        public LocalDateTime lastUsedAt;
    }
}
