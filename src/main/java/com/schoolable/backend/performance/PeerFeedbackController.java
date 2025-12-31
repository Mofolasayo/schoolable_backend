package com.schoolable.backend.performance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/performance/peer-feedback")
@Tag(name = "Peer Feedback", description = "Peer feedback submission and retrieval")
public class PeerFeedbackController {

    @Autowired
    private PeerFeedbackRepository peerFeedbackRepository;

    /**
     * Submit peer feedback for a colleague
     */
    @Operation(summary = "Submit peer feedback")
    @PostMapping
    public ResponseEntity<?> submitFeedback(
            @RequestBody PeerFeedbackRequest request,
            Authentication auth
    ) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID fromEmployeeId = (UUID) auth.getPrincipal();

        // Validate request
        if (request.toEmployeeId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "toEmployeeId is required"));
        }
        if (request.supportRating() < 1 || request.supportRating() > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "supportRating must be 1-5"));
        }

        // Don't allow self-feedback
        if (fromEmployeeId.equals(request.toEmployeeId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot submit feedback for yourself"));
        }

        // Get current quarter
        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        // Check if already submitted for this quarter
        List<PeerFeedback> existing = peerFeedbackRepository
                .findByFromEmployeeIdAndQuarterAndYear(fromEmployeeId, quarter, year);
        boolean alreadySubmitted = existing.stream()
                .anyMatch(f -> f.getToEmployeeId().equals(request.toEmployeeId()));
        
        if (alreadySubmitted) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "You have already submitted feedback for this person this quarter"
            ));
        }

        // Create feedback
        PeerFeedback feedback = new PeerFeedback();
        feedback.setFromEmployeeId(fromEmployeeId);
        feedback.setToEmployeeId(request.toEmployeeId());
        feedback.setQuarter(quarter);
        feedback.setYear(year);
        feedback.setSupportRating(request.supportRating());
        feedback.setCollaborationRating(request.collaborationRating());
        feedback.setCommunicationRating(request.communicationRating());
        feedback.setStrengths(request.strengths());
        feedback.setAreasForImprovement(request.areasForImprovement());
        feedback.setIsAnonymous(request.isAnonymous() != null ? request.isAnonymous() : true);
        feedback.setStatus("submitted");
        // createdAt is set automatically by @PrePersist

        peerFeedbackRepository.save(feedback);

        return ResponseEntity.ok(Map.of("success", true, "id", feedback.getId()));
    }

    /**
     * Get feedback received by an employee
     */
    @Operation(summary = "Get feedback received")
    @GetMapping("/received")
    public ResponseEntity<?> getReceivedFeedback(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID employeeId = (UUID) auth.getPrincipal();

        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        List<PeerFeedback> feedback = peerFeedbackRepository
                .findByToEmployeeIdAndQuarterAndYear(employeeId, quarter, year);

        // Return summary (anonymized)
        Map<String, Object> summary = new HashMap<>();
        summary.put("quarter", quarter);
        summary.put("year", year);
        summary.put("feedbackCount", feedback.size());
        
        if (!feedback.isEmpty()) {
            double avgSupport = feedback.stream()
                    .mapToInt(PeerFeedback::getSupportRating)
                    .average()
                    .orElse(0);
            double avgCollab = feedback.stream()
                    .filter(f -> f.getCollaborationRating() != null)
                    .mapToInt(PeerFeedback::getCollaborationRating)
                    .average()
                    .orElse(avgSupport);
            double avgComm = feedback.stream()
                    .filter(f -> f.getCommunicationRating() != null)
                    .mapToInt(PeerFeedback::getCommunicationRating)
                    .average()
                    .orElse(avgSupport);
            
            summary.put("averageSupportRating", Math.round(avgSupport * 100.0) / 100.0);
            summary.put("averageCollaborationRating", Math.round(avgCollab * 100.0) / 100.0);
            summary.put("averageCommunicationRating", Math.round(avgComm * 100.0) / 100.0);
            summary.put("overallAverage", Math.round((avgSupport + avgCollab + avgComm) / 3 * 100.0) / 100.0);
        }

        return ResponseEntity.ok(summary);
    }

    /**
     * Get feedback given by current user
     */
    @Operation(summary = "Get feedback given")
    @GetMapping("/given")
    public ResponseEntity<?> getGivenFeedback(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID employeeId = (UUID) auth.getPrincipal();

        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        List<PeerFeedback> feedback = peerFeedbackRepository
                .findByFromEmployeeIdAndQuarterAndYear(employeeId, quarter, year);

        List<Map<String, Object>> result = feedback.stream().map(f -> {
            Map<String, Object> item = new HashMap<>();
            item.put("toEmployeeId", f.getToEmployeeId());
            item.put("supportRating", f.getSupportRating());
            item.put("collaborationRating", f.getCollaborationRating());
            item.put("communicationRating", f.getCommunicationRating());
            item.put("createdAt", f.getCreatedAt());
            return item;
        }).toList();

        return ResponseEntity.ok(Map.of(
            "quarter", quarter,
            "year", year,
            "feedbackGiven", result
        ));
    }

    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    // Request DTO
    public record PeerFeedbackRequest(
            UUID toEmployeeId,
            int supportRating,
            Integer collaborationRating,
            Integer communicationRating,
            String strengths,
            String areasForImprovement,
            Boolean isAnonymous
    ) {}
}
