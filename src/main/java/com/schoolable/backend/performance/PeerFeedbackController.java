package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/performance/peer-feedback")
@Tag(name = "Peer Feedback", description = "Peer feedback submission and retrieval")
public class PeerFeedbackController {

    @Autowired
    private PeerFeedbackRepository peerFeedbackRepository;

    @Autowired
    private ProfileRepository profileRepository;

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

        // Get current quarter or use provided
        String quarter = request.quarter() != null ? request.quarter() : getCurrentQuarter();
        int year = request.year() != null ? request.year() : LocalDate.now().getYear();

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
        
        // New rating fields
        feedback.setAdaptabilityRating(request.adaptabilityRating());
        feedback.setValuesRating(request.valuesRating());
        feedback.setAccountabilityRating(request.accountabilityRating());
        feedback.setFeedbackRating(request.feedbackRating());
        
        // Leadership ratings (for rating team leads)
        feedback.setOrgGuidanceRating(request.orgGuidanceRating());
        feedback.setPeopleCultureRating(request.peopleCultureRating());
        feedback.setInfluenceRating(request.influenceRating());
        
        feedback.setStrengths(request.strengths());
        feedback.setAreasForImprovement(request.areasForImprovement());
        feedback.setIsAnonymous(request.isAnonymous() != null ? request.isAnonymous() : true);
        feedback.setStatus("submitted");

        peerFeedbackRepository.save(feedback);

        return ResponseEntity.ok(Map.of("success", true, "id", feedback.getId()));
    }

    /**
     * Get peer feedback submission status for current quarter
     */
    @Operation(summary = "Get peer feedback status")
    @GetMapping("/status")
    public ResponseEntity<?> getPeerFeedbackStatus(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        // Get team members in same department
        List<Profile> teamMembers = profileRepository.findByDepartment(profile.getDepartment());
        
        // Filter out self and get feedback status
        List<Map<String, Object>> memberStatus = teamMembers.stream()
            .filter(m -> !m.getId().equals(userId))
            .map(m -> {
                boolean submitted = peerFeedbackRepository.existsByFromEmployeeIdAndToEmployeeIdAndQuarterAndYear(
                    userId, m.getId(), quarter, year);
                return Map.<String, Object>of(
                    "id", m.getId().toString(),
                    "name", m.getFullName(),
                    "submitted", submitted
                );
            })
            .collect(Collectors.toList());

        long submittedCount = memberStatus.stream().filter(m -> (boolean) m.get("submitted")).count();

        return ResponseEntity.ok(Map.of(
            "quarter", quarter,
            "year", year,
            "submittedCount", submittedCount,
            "pendingCount", memberStatus.size() - submittedCount,
            "teamMembers", memberStatus
        ));
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
            double avgAdaptability = feedback.stream()
                    .filter(f -> f.getAdaptabilityRating() != null)
                    .mapToInt(PeerFeedback::getAdaptabilityRating)
                    .average()
                    .orElse(avgSupport);
            double avgValues = feedback.stream()
                    .filter(f -> f.getValuesRating() != null)
                    .mapToInt(PeerFeedback::getValuesRating)
                    .average()
                    .orElse(avgSupport);
            double avgAccountability = feedback.stream()
                    .filter(f -> f.getAccountabilityRating() != null)
                    .mapToInt(PeerFeedback::getAccountabilityRating)
                    .average()
                    .orElse(avgSupport);
            double avgFeedbackOpenness = feedback.stream()
                    .filter(f -> f.getFeedbackRating() != null)
                    .mapToInt(PeerFeedback::getFeedbackRating)
                    .average()
                    .orElse(avgSupport);
            
            summary.put("averages", Map.of(
                "supportRating", Math.round(avgSupport * 10.0) / 10.0,
                "collaborationRating", Math.round(avgCollab * 10.0) / 10.0,
                "adaptabilityRating", Math.round(avgAdaptability * 10.0) / 10.0,
                "valuesRating", Math.round(avgValues * 10.0) / 10.0,
                "accountabilityRating", Math.round(avgAccountability * 10.0) / 10.0,
                "feedbackRating", Math.round(avgFeedbackOpenness * 10.0) / 10.0,
                "overallRating", Math.round((avgSupport + avgCollab + avgAdaptability + avgValues + avgAccountability + avgFeedbackOpenness) / 6 * 10.0) / 10.0
            ));

            // Collect strengths and areas (anonymized)
            List<String> strengths = feedback.stream()
                .map(PeerFeedback::getStrengths)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());

            List<String> areasForImprovement = feedback.stream()
                .map(PeerFeedback::getAreasForImprovement)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());

            summary.put("strengths", strengths);
            summary.put("areasForImprovement", areasForImprovement);
        } else {
            summary.put("averages", Map.of(
                "supportRating", 0,
                "collaborationRating", 0,
                "adaptabilityRating", 0,
                "overallRating", 0
            ));
            summary.put("strengths", Collections.emptyList());
            summary.put("areasForImprovement", Collections.emptyList());
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
            item.put("adaptabilityRating", f.getAdaptabilityRating());
            item.put("valuesRating", f.getValuesRating());
            item.put("accountabilityRating", f.getAccountabilityRating());
            item.put("feedbackRating", f.getFeedbackRating());
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

    // Request DTO with all new rating fields
    public record PeerFeedbackRequest(
            UUID toEmployeeId,
            String quarter,
            Integer year,
            int supportRating,
            Integer collaborationRating,
            Integer communicationRating,
            Integer adaptabilityRating,
            Integer valuesRating,
            Integer accountabilityRating,
            Integer feedbackRating,
            Integer orgGuidanceRating,
            Integer peopleCultureRating,
            Integer influenceRating,
            String strengths,
            String areasForImprovement,
            Boolean isAnonymous
    ) {}
}
