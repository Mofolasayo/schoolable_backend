package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Employee Aura Dashboard API.
 * Provides endpoints for mobile app and web dashboard to display performance scores.
 */
@RestController
@RequestMapping("/api/performance")
public class AuraDashboardController {

    @Autowired
    private AuraDashboardService auraDashboardService;

    @Autowired
    private EnhancedAuraService enhancedAuraService;

    @Autowired
    private SubMetricCalculationService calculationService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AutoAuraCalculationService autoAuraService;

    /**
     * GET /api/performance/my-aura
     * Get the current user's Aura dashboard (for mobile app home screen)
     */
    @GetMapping("/my-aura")
    public ResponseEntity<?> getMyAura(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID employeeId = (UUID) auth.getPrincipal();
        AuraDashboardDto.EmployeeAuraResponse response = auraDashboardService.getEmployeeAuraDashboard(employeeId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/aura/dashboard
     * Alias for my-aura (for dashboard/mobile app compatibility)
     */
    @GetMapping("/aura/dashboard")
    public ResponseEntity<?> getAuraDashboard(
            Authentication auth,
            @RequestParam(required = false) String employeeId) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        UUID targetId;
        if (employeeId != null && !employeeId.isEmpty()) {
            targetId = UUID.fromString(employeeId);
        } else {
            targetId = (UUID) auth.getPrincipal();
        }
        
        AuraDashboardDto.EmployeeAuraResponse response = auraDashboardService.getEmployeeAuraDashboard(targetId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/employee/{employeeId}/aura
     * Get a specific employee's Aura dashboard (for managers/admins)
     */
    @GetMapping("/employee/{employeeId}/aura")
    public ResponseEntity<?> getEmployeeAura(
            @PathVariable String employeeId,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        AuraDashboardDto.EmployeeAuraResponse response = auraDashboardService.getEmployeeAuraDashboard(
            UUID.fromString(employeeId)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/my-trend
     * Get weekly rating trend for the current user (for charts)
     */
    @GetMapping("/my-trend")
    public ResponseEntity<?> getMyTrend(
            Authentication auth,
            @RequestParam(defaultValue = "12") int limit) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID employeeId = (UUID) auth.getPrincipal();
        AuraDashboardDto.WeeklyRatingsHistory history = auraDashboardService.getWeeklyTrend(employeeId, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/performance/employee/{employeeId}/trend
     * Get weekly rating trend for a specific employee
     */
    @GetMapping("/employee/{employeeId}/trend")
    public ResponseEntity<?> getEmployeeTrend(
            @PathVariable String employeeId,
            Authentication auth,
            @RequestParam(defaultValue = "12") int limit) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        AuraDashboardDto.WeeklyRatingsHistory history = auraDashboardService.getWeeklyTrend(
            UUID.fromString(employeeId), limit
        );
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/performance/aura
     * Get all employees with their Aura scores (for admin dashboard)
     * Returns pillar breakdown, certificates count, etc.
     */
    @GetMapping("/aura")
    public ResponseEntity<?> getAllEmployeesAura(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        // Get all employees with their Aura data
        var allEmployees = auraDashboardService.getAllEmployeesWithAura();
        return ResponseEntity.ok(allEmployees);
    }

    // ==================== ENHANCED AURA WITH SUB-METRICS ====================

    /**
     * GET /api/performance/my-aura/enhanced
     * Get the current user's enhanced Aura dashboard with sub-metric breakdown
     */
    @GetMapping("/my-aura/enhanced")
    public ResponseEntity<?> getMyEnhancedAura(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID employeeId = (UUID) auth.getPrincipal();
        AuraSubMetricDto.EnhancedAuraResponse response = enhancedAuraService.getEnhancedAuraDashboard(employeeId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/employee/{employeeId}/aura/enhanced
     * Get enhanced Aura dashboard for a specific employee (admin/manager)
     */
    @GetMapping("/employee/{employeeId}/aura/enhanced")
    public ResponseEntity<?> getEmployeeEnhancedAura(
            @PathVariable String employeeId,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        AuraSubMetricDto.EnhancedAuraResponse response = enhancedAuraService.getEnhancedAuraDashboard(
            UUID.fromString(employeeId)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/performance/recalculate
     * Trigger recalculation of all sub-metrics for all employees (admin only)
     */
    @PostMapping("/recalculate")
    public ResponseEntity<?> triggerRecalculation(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null || !"admin".equalsIgnoreCase(profile.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        calculationService.calculateAllEmployeeMetrics();
        return ResponseEntity.ok(Map.of("message", "Recalculation triggered successfully"));
    }

    /**
     * POST /api/performance/recalculate/{employeeId}
     * Trigger recalculation for a specific employee
     */
    @PostMapping("/recalculate/{employeeId}")
    public ResponseEntity<?> triggerEmployeeRecalculation(
            @PathVariable String employeeId,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null || !"admin".equalsIgnoreCase(profile.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        java.time.LocalDate now = java.time.LocalDate.now();
        int month = now.getMonthValue();
        String quarter = month <= 3 ? "Q1" : month <= 6 ? "Q2" : month <= 9 ? "Q3" : "Q4";
        
        calculationService.calculateEmployeeMetrics(UUID.fromString(employeeId), quarter, now.getYear());
        return ResponseEntity.ok(Map.of("message", "Employee metrics recalculated"));
    }

    /**
     * POST /api/performance/admin/rate
     * Admin rates a leadership technical metric or leadership pillar metric
     */
    @PostMapping("/admin/rate")
    public ResponseEntity<?> adminRateMetric(
            @RequestBody AuraSubMetricDto.AdminRatingRequest request,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        UUID adminId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(adminId).orElse(null);
        if (profile == null || !"admin".equalsIgnoreCase(profile.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        
        try {
            UUID employeeId = UUID.fromString(request.getEmployeeId());
            String subMetric = request.getSubMetric();
            
            // Determine if this is a leadership pillar metric or technical leadership metric
            if (subMetric.startsWith("leadership_") || 
                subMetric.equals("organizational_guidance") ||
                subMetric.equals("people_culture_leadership") ||
                subMetric.equals("executive_decision_making") ||
                subMetric.equals("crisis_conflict_handling") ||
                subMetric.equals("leadership_influence")) {
                enhancedAuraService.rateLeadershipPillarMetric(employeeId, subMetric, request.getScore(), adminId);
            } else {
                enhancedAuraService.rateLeadershipMetric(employeeId, subMetric, request.getScore(), adminId);
            }
            
            return ResponseEntity.ok(Map.of("message", "Rating saved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== FULLY AUTOMATED AURA (Department-Specific KPIs) ====================

    /**
     * GET /api/performance/my-aura/auto
     * Get fully automated Aura score using department-specific KPIs.
     * This calculates the score in real-time from system data.
     */
    @GetMapping("/my-aura/auto")
    public ResponseEntity<?> getMyAutoAura(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID employeeId = (UUID) auth.getPrincipal();
        Map<String, Object> response = autoAuraService.calculateEmployeeScore(employeeId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/employee/{employeeId}/aura/auto
     * Get fully automated Aura for a specific employee (admin/manager view)
     */
    @GetMapping("/employee/{employeeId}/aura/auto")
    public ResponseEntity<?> getEmployeeAutoAura(
            @PathVariable String employeeId,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        Map<String, Object> response = autoAuraService.calculateEmployeeScore(UUID.fromString(employeeId));
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/department-kpis
     * Get available department profiles and their automation rates
     */
    @GetMapping("/department-kpis")
    public ResponseEntity<?> getDepartmentKpis() {
        var departments = DepartmentKpiConfig.getAllDepartments();
        var profiles = new java.util.ArrayList<Map<String, Object>>();
        
        for (String dept : departments) {
            var profile = DepartmentKpiConfig.DEPARTMENT_PROFILES.get(dept);
            profiles.add(Map.of(
                "key", dept,
                "name", profile.displayName,
                "automationRate", Math.round(profile.getAutomationPercentage()),
                "totalMetrics", profile.getTotalMetricCount(),
                "autoMetrics", profile.getAutoMetricCount()
            ));
        }
        
        // Add default profile info
        var defaultProfile = DepartmentKpiConfig.DEFAULT_PROFILE;
        profiles.add(Map.of(
            "key", "default",
            "name", "General (Default)",
            "automationRate", Math.round(defaultProfile.getAutomationPercentage()),
            "totalMetrics", defaultProfile.getTotalMetricCount(),
            "autoMetrics", defaultProfile.getAutoMetricCount()
        ));
        
        return ResponseEntity.ok(Map.of(
            "departments", profiles,
            "message", "Each department has custom KPIs optimized for their role"
        ));
    }

    /**
     * POST /api/performance/auto-recalculate
     * Trigger automated recalculation for all employees using department KPIs
     */
    @PostMapping("/auto-recalculate")
    public ResponseEntity<?> triggerAutoRecalculation(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        // Run calculation asynchronously
        new Thread(() -> {
            autoAuraService.calculateAllEmployeeScores();
        }).start();
        
        return ResponseEntity.ok(Map.of(
            "message", "Auto-recalculation started in background",
            "note", "This runs automatically every Sunday at 2 AM"
        ));
    }
}


