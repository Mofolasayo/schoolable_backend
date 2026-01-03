package com.schoolable.backend.kpi;

import com.schoolable.backend.audit.AuditService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for managing dynamic department KPI configurations.
 * Allows admins to configure department profiles and team leads to create team KPIs.
 */
@RestController
@RequestMapping("/api/kpi/config")
public class DynamicKpiController {

    @Autowired
    private DynamicKpiService dynamicKpiService;

    @Autowired
    private DepartmentKpiProfileRepository profileRepository;

    @Autowired
    private DepartmentPillarRepository pillarRepository;

    @Autowired
    private DepartmentMetricRepository metricRepository;

    @Autowired
    private ProfileRepository userProfileRepository;

    @Autowired
    private AuditService auditService;

    /**
     * Get all department KPI profiles.
     */
    @GetMapping("/departments")
    public ResponseEntity<?> getAllDepartments() {
        List<Map<String, Object>> departments = dynamicKpiService.getDepartmentAutomationStats();
        return ResponseEntity.ok(departments);
    }

    /**
     * Get KPI profile for a specific department.
     */
    @GetMapping("/departments/{department}")
    public ResponseEntity<?> getDepartmentProfile(@PathVariable String department) {
        DepartmentKpiProfile profile = dynamicKpiService.getProfileForDepartment(department);
        return ResponseEntity.ok(toProfileDto(profile));
    }

    /**
     * Create a new department KPI profile (Admin only).
     */
    @PostMapping("/departments")
    public ResponseEntity<?> createDepartmentProfile(
            Authentication auth,
            @RequestBody CreateProfileRequest request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        
        try {
            DepartmentKpiProfile profile = dynamicKpiService.createProfile(
                request.department(),
                request.displayName(),
                request.description(),
                userId
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "profile", toProfileDto(profile)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Clone an existing profile for a new department.
     */
    @PostMapping("/departments/clone")
    public ResponseEntity<?> cloneDepartmentProfile(
            Authentication auth,
            @RequestBody CloneProfileRequest request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        
        try {
            DepartmentKpiProfile profile = dynamicKpiService.cloneProfile(
                request.sourceDepartment(),
                request.targetDepartment(),
                request.displayName(),
                userId
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "profile", toProfileDto(profile)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Add a pillar to a department profile.
     */
    @PostMapping("/departments/{profileId}/pillars")
    public ResponseEntity<?> addPillar(
            Authentication auth,
            @PathVariable UUID profileId,
            @RequestBody AddPillarRequest request
    ) {
        try {
            DepartmentPillar pillar = dynamicKpiService.addPillar(
                profileId,
                request.pillarKey(),
                request.displayName(),
                request.weight(),
                request.sortOrder()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "pillar", toPillarDto(pillar)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Update a pillar's weight.
     */
    @PatchMapping("/pillars/{pillarId}")
    public ResponseEntity<?> updatePillar(
            Authentication auth,
            @PathVariable UUID pillarId,
            @RequestBody UpdatePillarRequest request
    ) {
        try {
            DepartmentPillar pillar = dynamicKpiService.updatePillarWeight(pillarId, request.weight());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "pillar", toPillarDto(pillar)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Add a metric to a pillar.
     */
    @PostMapping("/pillars/{pillarId}/metrics")
    public ResponseEntity<?> addMetric(
            Authentication auth,
            @PathVariable UUID pillarId,
            @RequestBody AddMetricRequest request
    ) {
        try {
            DepartmentMetric metric = dynamicKpiService.addMetric(
                pillarId,
                request.metricKey(),
                request.displayName(),
                request.weight(),
                request.source(),
                request.dataSource(),
                request.targetValue() != null ? BigDecimal.valueOf(request.targetValue()) : null,
                request.description()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "metric", toMetricDto(metric)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Update a metric.
     */
    @PatchMapping("/metrics/{metricId}")
    public ResponseEntity<?> updateMetric(
            Authentication auth,
            @PathVariable UUID metricId,
            @RequestBody UpdateMetricRequest request
    ) {
        try {
            DepartmentMetric metric = dynamicKpiService.updateMetric(
                metricId,
                request.weight(),
                request.displayName(),
                request.targetValue() != null ? BigDecimal.valueOf(request.targetValue()) : null,
                request.description(),
                request.isActive()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "metric", toMetricDto(metric)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Delete a metric.
     */
    @DeleteMapping("/metrics/{metricId}")
    public ResponseEntity<?> deleteMetric(
            Authentication auth,
            @PathVariable UUID metricId
    ) {
        try {
            dynamicKpiService.deleteMetric(metricId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Metric deleted successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get my department's KPI profile (for team leads).
     */
    @GetMapping("/my-department")
    public ResponseEntity<?> getMyDepartmentProfile(Authentication auth) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        
        return userProfileRepository.findById(userId)
            .map(profile -> {
                String department = profile.getDepartment();
                if (department == null || department.isBlank()) {
                    department = "Default";
                }
                DepartmentKpiProfile kpiProfile = dynamicKpiService.getProfileForDepartment(department);
                return ResponseEntity.ok(toProfileDto(kpiProfile));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // DTO Converters
    private Map<String, Object> toProfileDto(DepartmentKpiProfile profile) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", profile.getId());
        dto.put("department", profile.getDepartment());
        dto.put("displayName", profile.getDisplayName());
        dto.put("description", profile.getDescription());
        dto.put("isActive", profile.getIsActive());
        dto.put("totalWeight", profile.getTotalWeight());
        dto.put("totalMetrics", profile.getTotalMetricCount());
        dto.put("autoMetrics", profile.getAutoMetricCount());
        dto.put("automationPercentage", profile.getAutomationPercentage());
        dto.put("pillars", profile.getPillars().stream()
            .map(this::toPillarDto)
            .collect(Collectors.toList()));
        return dto;
    }

    private Map<String, Object> toPillarDto(DepartmentPillar pillar) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", pillar.getId());
        dto.put("pillarKey", pillar.getPillarKey());
        dto.put("displayName", pillar.getDisplayName());
        dto.put("weight", pillar.getWeight());
        dto.put("sortOrder", pillar.getSortOrder());
        dto.put("isActive", pillar.getIsActive());
        dto.put("totalMetricWeight", pillar.getTotalMetricWeight());
        dto.put("metrics", pillar.getMetrics().stream()
            .map(this::toMetricDto)
            .collect(Collectors.toList()));
        return dto;
    }

    private Map<String, Object> toMetricDto(DepartmentMetric metric) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", metric.getId());
        dto.put("metricKey", metric.getMetricKey());
        dto.put("displayName", metric.getDisplayName());
        dto.put("weight", metric.getWeightInPillar());
        dto.put("source", metric.getSource());
        dto.put("dataSource", metric.getDataSource());
        dto.put("targetValue", metric.getTargetValue());
        dto.put("targetUnit", metric.getTargetUnit());
        dto.put("description", metric.getDescription());
        dto.put("isAutoCalculated", metric.getIsAutoCalculated());
        dto.put("isActive", metric.getIsActive());
        dto.put("sortOrder", metric.getSortOrder());
        return dto;
    }

    // Request DTOs
    record CreateProfileRequest(String department, String displayName, String description) {}
    record CloneProfileRequest(String sourceDepartment, String targetDepartment, String displayName) {}
    record AddPillarRequest(String pillarKey, String displayName, Integer weight, Integer sortOrder) {}
    record UpdatePillarRequest(Integer weight) {}
    record AddMetricRequest(
        String metricKey, 
        String displayName, 
        Integer weight, 
        String source, 
        String dataSource,
        Double targetValue,
        String description
    ) {}
    record UpdateMetricRequest(
        Integer weight, 
        String displayName, 
        Double targetValue, 
        String description,
        Boolean isActive
    ) {}
}
