package com.schoolable.backend.performance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Weekly Performance Reports
 * 
 * Team leads upload weekly ratings for their team members.
 * These are aggregated at end of quarter to calculate Aura.
 * 
 * Endpoints:
 * - POST /api/performance/weekly - Submit single weekly report
 * - POST /api/performance/weekly/batch - Submit batch weekly reports
 * - GET /api/performance/weekly?week=1&year=2026 - Get all reports for a week (admin)
 * - GET /api/performance/weekly/team/{teamLeadId}?week=1&year=2026 - Get team lead's submissions
 * - GET /api/performance/weekly/employee/{employeeId}?year=2026 - Get employee trend
 */
@RestController
@RequestMapping("/api/performance/weekly")
@CrossOrigin(origins = "*")
@Tag(name = "Weekly Reports", description = "Weekly performance rating APIs - aggregated quarterly")
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    public WeeklyReportController(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    /**
     * Team Lead submits a weekly report for a single team member
     * 
     * POST /api/performance/weekly
     * 
     * Request body:
     * {
     *   "employeeId": "uuid",
     *   "weekNumber": 1,
     *   "year": 2026,
     *   "technicalScore": 4,      // 1-5 scale
     *   "behavioralScore": 4,
     *   "cultureFitScore": 5,
     *   "growthLearningScore": 3,
     *   "technicalNotes": "...",
     *   "weeklyHighlights": "...",
     *   "areasForFocus": "..."
     * }
     */
    @PostMapping
    public ResponseEntity<?> submitWeeklyReport(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody WeeklyReportDto.SingleReportRequest request) {
        try {
            UUID teamLeadId = UUID.fromString(userId);
            WeeklyReportDto.ReportResponse response = weeklyReportService.submitReport(teamLeadId, request);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Weekly report submitted successfully");
            result.put("report", response);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Team Lead submits batch weekly reports for all team members
     * 
     * POST /api/performance/weekly/batch
     * 
     * Request body:
     * {
     *   "weekNumber": 1,
     *   "year": 2026,
     *   "reports": [
     *     {
     *       "employeeId": "uuid1",
     *       "technicalScore": 4,
     *       "behavioralScore": 4,
     *       ...
     *     },
     *     ...
     *   ]
     * }
     */
    @PostMapping("/batch")
    public ResponseEntity<?> submitBatchWeeklyReports(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody WeeklyReportDto.BatchReportRequest request) {
        try {
            UUID teamLeadId = UUID.fromString(userId);
            List<WeeklyReportDto.ReportResponse> responses = weeklyReportService.submitBatchReports(teamLeadId, request);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", String.format("%d weekly reports submitted for Week %d, %d", 
                responses.size(), request.getWeekNumber(), request.getYear()));
            result.put("reports", responses);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * SIMPLIFIED: Team Lead submits the 3 key ratings for all team members
     * 
     * POST /api/performance/weekly/simplified
     * 
     * Request body:
     * {
     *   "weekNumber": 1,
     *   "year": 2026,
     *   "teamReportUrl": "https://...",
     *   "ratings": [
     *     {
     *       "employeeId": "uuid1",
     *       "teamworkCollaborationScore": 4,
     *       "initiativeScore": 5,
     *       "attitudeTowardsWorkScore": 4,
     *       "notes": "Great work this week"
     *     },
     *     ...
     *   ]
     * }
     */
    @PostMapping("/simplified")
    @Operation(summary = "Submit simplified weekly ratings (3 items per employee)")
    public ResponseEntity<?> submitSimplifiedReports(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody WeeklyReportDto.SimplifiedBatchRequest request) {
        try {
            UUID teamLeadId = UUID.fromString(userId);
            List<WeeklyReportDto.ReportResponse> responses = weeklyReportService.submitSimplifiedReports(teamLeadId, request);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", String.format("%d simplified reports submitted for Week %d, %d", 
                responses.size(), request.getWeekNumber(), request.getYear()));
            result.put("reports", responses);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get all weekly reports for a specific week (Admin view)
     * 
     * GET /api/performance/weekly?week=1&year=2026
     */
    @GetMapping
    public ResponseEntity<?> getWeeklyReports(
            @RequestParam Integer week,
            @RequestParam Integer year) {
        try {
            List<WeeklyReportDto.ReportResponse> reports = weeklyReportService.getWeeklyReports(week, year);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("weekNumber", week);
            result.put("year", year);
            result.put("count", reports.size());
            result.put("reports", reports);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get reports submitted by a team lead for a specific week
     * 
     * GET /api/performance/weekly/team/{teamLeadId}?week=1&year=2026
     */
    @GetMapping("/team/{teamLeadId}")
    public ResponseEntity<?> getTeamLeadWeeklyReports(
            @PathVariable String teamLeadId,
            @RequestParam Integer week,
            @RequestParam Integer year) {
        try {
            UUID leadId = UUID.fromString(teamLeadId);
            List<WeeklyReportDto.ReportResponse> reports = weeklyReportService.getTeamLeadWeeklyReports(leadId, week, year);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("teamLeadId", teamLeadId);
            result.put("weekNumber", week);
            result.put("year", year);
            result.put("count", reports.size());
            result.put("reports", reports);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get employee's weekly performance trend for a year
     * 
     * GET /api/performance/weekly/employee/{employeeId}?year=2026
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeWeeklyTrend(
            @PathVariable String employeeId,
            @RequestParam(required = false) Integer year) {
        try {
            UUID empId = UUID.fromString(employeeId);
            
            List<WeeklyReportDto.ReportResponse> reports;
            if (year != null) {
                reports = weeklyReportService.getEmployeeWeeklyTrend(empId, year);
            } else {
                reports = weeklyReportService.getEmployeeHistory(empId);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("employeeId", employeeId);
            if (year != null) result.put("year", year);
            result.put("count", reports.size());
            result.put("reports", reports);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * TEMPORARY: Delete a corrupted weekly report record
     * DELETE /api/performance/weekly/{reportId}
     * 
     * This is a temporary endpoint to clean up corrupted records
     */
    @DeleteMapping("/{reportId}")
    @Operation(summary = "Delete a weekly report (Admin only - temporary cleanup endpoint)")
    public ResponseEntity<?> deleteWeeklyReport(@PathVariable Long reportId) {
        try {
            weeklyReportService.deleteReport(reportId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Report deleted successfully");
            result.put("reportId", reportId);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
