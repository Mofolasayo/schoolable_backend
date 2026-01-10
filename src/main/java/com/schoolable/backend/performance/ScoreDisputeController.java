package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Score Dispute Controller
 * Handles score dispute submission and review workflow.
 */
@RestController
@RequestMapping({"/api/score-disputes", "/score-disputes"})
@Tag(name = "Score Disputes", description = "Submit and review score disputes")
public class ScoreDisputeController {

    @Autowired
    private ScoreDisputeRepository disputeRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AuraScoreJobService auraScoreJobService;

    /**
     * Submit a score dispute
     */
    @PostMapping
    @Operation(summary = "Submit dispute", description = "Submit a dispute for your score")
    public ResponseEntity<?> submitDispute(@RequestBody DisputeRequest req, Authentication auth) {
        UUID employeeId = getUserId(auth);

        if (req.scoreType == null || req.disputedScore == null || req.reason == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "scoreType, disputedScore, and reason are required"));
        }

        ScoreDispute dispute = new ScoreDispute(
            employeeId, req.scoreType, req.disputedScore, req.reason);
        dispute.setPillarKey(req.pillarKey);
        dispute.setMetricKey(req.metricKey);

        disputeRepository.save(dispute);

        return ResponseEntity.ok(Map.of(
            "message", "Dispute submitted successfully",
            "disputeId", dispute.getId().toString(),
            "status", dispute.getStatus().name()
        ));
    }

    /**
     * Get my disputes
     */
    @GetMapping("/me")
    @Operation(summary = "Get my disputes")
    public ResponseEntity<?> getMyDisputes(Authentication auth) {
        UUID employeeId = getUserId(auth);
        List<ScoreDispute> disputes = disputeRepository.findByEmployeeIdOrderBySubmittedAtDesc(employeeId);
        
        return ResponseEntity.ok(Map.of(
            "disputes", disputes.stream().map(this::buildDisputeResponse).collect(Collectors.toList())
        ));
    }

    /**
     * Get pending disputes (for reviewers)
     */
    @GetMapping("/pending")
    @Operation(summary = "Get pending disputes", description = "Get all disputes awaiting review (admin only)")
    public ResponseEntity<?> getPendingDisputes(Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        List<ScoreDispute> submitted = disputeRepository
            .findByStatusOrderBySubmittedAtAsc(ScoreDispute.DisputeStatus.SUBMITTED);
        List<ScoreDispute> underReview = disputeRepository
            .findByStatusOrderBySubmittedAtAsc(ScoreDispute.DisputeStatus.UNDER_REVIEW);

        List<ScoreDispute> all = new ArrayList<>();
        all.addAll(submitted);
        all.addAll(underReview);

        return ResponseEntity.ok(Map.of(
            "pendingCount", all.size(),
            "disputes", all.stream().map(this::buildDisputeResponse).collect(Collectors.toList())
        ));
    }

    /**
     * Start reviewing a dispute
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "Start review", description = "Mark dispute as under review")
    public ResponseEntity<?> startReview(@PathVariable UUID id, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        UUID reviewerId = getUserId(auth);
        
        ScoreDispute dispute = disputeRepository.findById(id).orElse(null);
        if (dispute == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Dispute not found"));
        }

        dispute.startReview(reviewerId);
        disputeRepository.save(dispute);

        return ResponseEntity.ok(buildDisputeResponse(dispute));
    }

    /**
     * Adjust a disputed score
     */
    @PostMapping("/{id}/adjust")
    @Operation(summary = "Adjust score", description = "Adjust the score and resolve dispute")
    public ResponseEntity<?> adjustScore(
            @PathVariable UUID id,
            @RequestBody AdjustmentRequest req,
            Authentication auth) {
        
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        UUID reviewerId = getUserId(auth);
        
        ScoreDispute dispute = disputeRepository.findById(id).orElse(null);
        if (dispute == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Dispute not found"));
        }

        if (req.adjustedScore == null || req.notes == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "adjustedScore and notes are required"));
        }

        dispute.adjust(reviewerId, req.adjustedScore, req.notes);
        disputeRepository.save(dispute);

        auraScoreJobService.enqueueJob(
            AuraScoreJobTypes.AUTO_RECALCULATE_EMPLOYEE,
            Map.of(
                "employeeId", dispute.getEmployeeId().toString(),
                "requestedBy", reviewerId.toString()
            ),
            3,
            reviewerId
        );

        return ResponseEntity.ok(Map.of(
            "message", "Score adjusted successfully",
            "dispute", buildDisputeResponse(dispute)
        ));
    }

    /**
     * Deny a dispute
     */
    @PostMapping("/{id}/deny")
    @Operation(summary = "Deny dispute", description = "Deny the dispute with explanation")
    public ResponseEntity<?> denyDispute(
            @PathVariable UUID id,
            @RequestBody DenialRequest req,
            Authentication auth) {
        
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        UUID reviewerId = getUserId(auth);
        
        ScoreDispute dispute = disputeRepository.findById(id).orElse(null);
        if (dispute == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Dispute not found"));
        }

        if (req.notes == null || req.notes.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Denial reason is required"));
        }

        dispute.deny(reviewerId, req.notes);
        disputeRepository.save(dispute);

        return ResponseEntity.ok(Map.of(
            "message", "Dispute denied",
            "dispute", buildDisputeResponse(dispute)
        ));
    }

    private UUID getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        return (UUID) auth.getPrincipal();
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    private Map<String, Object> buildDisputeResponse(ScoreDispute d) {
        Profile employee = profileRepository.findById(d.getEmployeeId()).orElse(null);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", d.getId().toString());
        result.put("employeeId", d.getEmployeeId().toString());
        result.put("employeeName", employee != null ? employee.getFullName() : "Unknown");
        result.put("scoreType", d.getScoreType());
        result.put("disputedScore", d.getDisputedScore());
        result.put("reason", d.getDisputeReason());
        result.put("status", d.getStatus().name());
        result.put("submittedAt", d.getSubmittedAt().toString());
        
        if (d.getPillarKey() != null) {
            result.put("pillarKey", d.getPillarKey());
        }
        if (d.getMetricKey() != null) {
            result.put("metricKey", d.getMetricKey());
        }
        if (d.getReviewedAt() != null) {
            result.put("reviewedAt", d.getReviewedAt().toString());
        }
        if (d.getResolutionNotes() != null) {
            result.put("resolutionNotes", d.getResolutionNotes());
        }
        if (d.getAdjustedScore() != null) {
            result.put("adjustedScore", d.getAdjustedScore());
        }

        return result;
    }

    // Request DTOs
    public static class DisputeRequest {
        public String scoreType;
        public BigDecimal disputedScore;
        public String reason;
        public String pillarKey;
        public String metricKey;
    }

    public static class AdjustmentRequest {
        public BigDecimal adjustedScore;
        public String notes;
    }

    public static class DenialRequest {
        public String notes;
    }
}
