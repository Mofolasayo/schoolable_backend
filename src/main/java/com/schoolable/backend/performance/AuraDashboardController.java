package com.schoolable.backend.performance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<AuraDashboardDto.EmployeeAuraResponse> getMyAura(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID employeeId = UUID.fromString(userDetails.getUsername());
        AuraDashboardDto.EmployeeAuraResponse response = auraDashboardService.getEmployeeAuraDashboard(employeeId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/performance/employee/{employeeId}/aura
     * Get a specific employee's Aura dashboard (for managers/admins)
     */
    @GetMapping("/employee/{employeeId}/aura")
    public ResponseEntity<AuraDashboardDto.EmployeeAuraResponse> getEmployeeAura(
            @PathVariable String employeeId) {
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
    public ResponseEntity<AuraDashboardDto.WeeklyRatingsHistory> getMyTrend(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "12") int limit) {
        UUID employeeId = UUID.fromString(userDetails.getUsername());
        AuraDashboardDto.WeeklyRatingsHistory history = auraDashboardService.getWeeklyTrend(employeeId, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/performance/employee/{employeeId}/trend
     * Get weekly rating trend for a specific employee
     */
    @GetMapping("/employee/{employeeId}/trend")
    public ResponseEntity<AuraDashboardDto.WeeklyRatingsHistory> getEmployeeTrend(
            @PathVariable String employeeId,
            @RequestParam(defaultValue = "12") int limit) {
        AuraDashboardDto.WeeklyRatingsHistory history = auraDashboardService.getWeeklyTrend(
            UUID.fromString(employeeId), limit
        );
        return ResponseEntity.ok(history);
    }
}
