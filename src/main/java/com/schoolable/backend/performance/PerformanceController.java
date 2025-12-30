package com.schoolable.backend.performance;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Performance Management APIs
 * 
 * Endpoints:
 * - POST /api/performance/assess - Team Lead submits single assessment
 * - POST /api/performance/assess/batch - Team Lead submits multiple assessments
 * - GET /api/performance/reviews - Get quarterly reviews (admin)
 * - GET /api/performance/reviews/pending - Get pending reviews (admin)
 * - GET /api/performance/reviews/team/{teamLeadId} - Get team lead's submissions
 * - GET /api/performance/reviews/employee/{employeeId} - Get employee history
 * - POST /api/performance/reviews/{id}/approve - Approve a review (admin)
 * - GET /api/performance/summary - Get review summary stats
 */
@RestController
@RequestMapping("/api/performance")
@CrossOrigin(origins = "*")
public class PerformanceController {

    private final PerformanceReviewService reviewService;

    public PerformanceController(PerformanceReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Team Lead submits a performance assessment for a single team member
     * 
     * POST /api/performance/assess
     * 
     * Request body:
     * {
     *   "employeeId": "uuid",
     *   "quarter": "Q1",
     *   "reviewYear": 2026,
     *   "technicalScore": 85.0,
     *   "behavioralScore": 80.0,
     *   "cultureFitScore": 90.0,
     *   "growthLearningScore": 75.0,
     *   "technicalComments": "...",
     *   "behavioralComments": "...",
     *   "cultureFitComments": "...",
     *   "growthLearningComments": "...",
     *   "strengths": "...",
     *   "areasForImprovement": "...",
     *   "overallComments": "...",
     *   "submitForApproval": true
     * }
     */
    @PostMapping("/assess")
    public ResponseEntity<?> submitAssessment(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody PerformanceReviewDto.TeamLeadAssessmentRequest request) {
        try {
            UUID teamLeadId = UUID.fromString(userId);
            PerformanceReviewDto.ReviewResponse response = reviewService.submitAssessment(teamLeadId, request);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", request.isSubmitForApproval() 
                ? "Assessment submitted for approval" 
                : "Assessment saved as draft");
            result.put("review", response);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Team Lead submits assessments for multiple team members at once
     * 
     * POST /api/performance/assess/batch
     * 
     * Request body:
     * {
     *   "quarter": "Q1",
     *   "reviewYear": 2026,
     *   "submitForApproval": true,
     *   "assessments": [
     *     {
     *       "employeeId": "uuid1",
     *       "technicalScore": 85.0,
     *       "behavioralScore": 80.0,
     *       ...
     *     },
     *     {
     *       "employeeId": "uuid2",
     *       ...
     *     }
     *   ]
     * }
     */
    @PostMapping("/assess/batch")
    public ResponseEntity<?> submitBatchAssessments(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody PerformanceReviewDto.BatchTeamAssessmentRequest request) {
        try {
            UUID teamLeadId = UUID.fromString(userId);
            List<PerformanceReviewDto.ReviewResponse> responses = reviewService.submitBatchAssessments(teamLeadId, request);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", String.format("%d assessments processed", responses.size()));
            result.put("reviews", responses);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get all reviews for a specific quarter (Admin only)
     * 
     * GET /api/performance/reviews?quarter=Q1&year=2026
     */
    @GetMapping("/reviews")
    public ResponseEntity<?> getQuarterlyReviews(
            @RequestParam String quarter,
            @RequestParam Integer year) {
        try {
            List<PerformanceReviewDto.ReviewResponse> reviews = reviewService.getQuarterlyReviews(quarter, year);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("quarter", quarter);
            result.put("year", year);
            result.put("count", reviews.size());
            result.put("reviews", reviews);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get reviews pending approval (Admin only)
     * 
     * GET /api/performance/reviews/pending?quarter=Q1&year=2026
     */
    @GetMapping("/reviews/pending")
    public ResponseEntity<?> getPendingReviews(
            @RequestParam String quarter,
            @RequestParam Integer year) {
        try {
            List<PerformanceReviewDto.ReviewResponse> reviews = reviewService.getPendingReviews(quarter, year);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", reviews.size());
            result.put("reviews", reviews);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get reviews submitted by a specific team lead
     * 
     * GET /api/performance/reviews/team/{teamLeadId}?quarter=Q1&year=2026
     */
    @GetMapping("/reviews/team/{teamLeadId}")
    public ResponseEntity<?> getTeamLeadReviews(
            @PathVariable String teamLeadId,
            @RequestParam String quarter,
            @RequestParam Integer year) {
        try {
            UUID leadId = UUID.fromString(teamLeadId);
            List<PerformanceReviewDto.ReviewResponse> reviews = reviewService.getTeamLeadReviews(leadId, quarter, year);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("teamLeadId", teamLeadId);
            result.put("count", reviews.size());
            result.put("reviews", reviews);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get an employee's review history
     * 
     * GET /api/performance/reviews/employee/{employeeId}
     */
    @GetMapping("/reviews/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeReviewHistory(@PathVariable String employeeId) {
        try {
            UUID empId = UUID.fromString(employeeId);
            List<PerformanceReviewDto.ReviewResponse> reviews = reviewService.getEmployeeReviewHistory(empId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("employeeId", employeeId);
            result.put("count", reviews.size());
            result.put("reviews", reviews);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Admin approves a submitted review
     * 
     * POST /api/performance/reviews/{id}/approve
     */
    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<?> approveReview(
            @PathVariable Long id,
            @RequestHeader("X-User-ID") String adminId) {
        try {
            UUID admin = UUID.fromString(adminId);
            PerformanceReviewDto.ReviewResponse response = reviewService.approveReview(id, admin);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Review approved successfully");
            result.put("review", response);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get review summary statistics
     * 
     * GET /api/performance/summary?quarter=Q1&year=2026
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getReviewSummary(
            @RequestParam String quarter,
            @RequestParam Integer year) {
        try {
            PerformanceReviewDto.ReviewSummary summary = reviewService.getReviewSummary(quarter, year);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("summary", summary);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
