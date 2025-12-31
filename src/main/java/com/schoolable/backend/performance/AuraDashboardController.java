package com.schoolable.backend.performance;

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
}

