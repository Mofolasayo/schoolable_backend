package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PerformanceReviewService {

    private final PerformanceReviewRepository reviewRepository;
    private final ProfileRepository profileRepository;

    public PerformanceReviewService(PerformanceReviewRepository reviewRepository,
                                    ProfileRepository profileRepository) {
        this.reviewRepository = reviewRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Team Lead submits a single performance review for a team member
     */
    @Transactional
    public PerformanceReviewDto.ReviewResponse submitAssessment(
            UUID teamLeadId,
            PerformanceReviewDto.TeamLeadAssessmentRequest request) {

        // Verify team lead exists and is actually a team lead
        Profile teamLead = profileRepository.findById(teamLeadId)
                .orElseThrow(() -> new RuntimeException("Team lead not found"));

        if (!Boolean.TRUE.equals(teamLead.getIsTeamLead())) {
            throw new RuntimeException("Only team leads can submit assessments");
        }

        UUID employeeId = UUID.fromString(request.getEmployeeId());

        // Check if review already exists for this quarter
        Optional<PerformanceReview> existingReview = reviewRepository
                .findByEmployeeIdAndQuarterAndReviewYear(employeeId, request.getQuarter(), request.getReviewYear());

        PerformanceReview review;
        if (existingReview.isPresent()) {
            review = existingReview.get();
            // Only allow updates if still in draft status
            if (!"draft".equals(review.getStatus())) {
                throw new RuntimeException("Review already submitted and cannot be modified");
            }
        } else {
            review = new PerformanceReview();
            review.setEmployeeId(employeeId);
            review.setQuarter(request.getQuarter());
            review.setReviewYear(request.getReviewYear());
        }

        // Set scores
        review.setTechnicalScore(request.getTechnicalScore());
        review.setBehavioralScore(request.getBehavioralScore());
        review.setCultureFitScore(request.getCultureFitScore());
        review.setGrowthLearningScore(request.getGrowthLearningScore());

        // Set comments
        review.setTechnicalComments(request.getTechnicalComments());
        review.setBehavioralComments(request.getBehavioralComments());
        review.setCultureFitComments(request.getCultureFitComments());
        review.setGrowthLearningComments(request.getGrowthLearningComments());
        review.setStrengths(request.getStrengths());
        review.setAreasForImprovement(request.getAreasForImprovement());
        review.setComments(request.getOverallComments());

        // Set metadata
        review.setReviewerId(teamLeadId);
        review.setReviewDate(LocalDate.now());

        // Check if employee being reviewed is a team lead
        Profile employee = profileRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        review.setIsTeamLeadReview(Boolean.TRUE.equals(employee.getIsTeamLead()));

        // Set status based on submission flag
        if (request.isSubmitForApproval()) {
            review.setStatus("submitted");
            review.setSubmittedAt(OffsetDateTime.now());
        } else {
            review.setStatus("draft");
        }

        PerformanceReview saved = reviewRepository.save(review);
        return mapToResponse(saved, employee, teamLead);
    }

    /**
     * Team Lead submits batch assessments for multiple team members
     */
    @Transactional
    public List<PerformanceReviewDto.ReviewResponse> submitBatchAssessments(
            UUID teamLeadId,
            PerformanceReviewDto.BatchTeamAssessmentRequest request) {

        Profile teamLead = profileRepository.findById(teamLeadId)
                .orElseThrow(() -> new RuntimeException("Team lead not found"));

        if (!Boolean.TRUE.equals(teamLead.getIsTeamLead())) {
            throw new RuntimeException("Only team leads can submit assessments");
        }

        List<PerformanceReviewDto.ReviewResponse> responses = new ArrayList<>();

        for (PerformanceReviewDto.TeamMemberAssessment assessment : request.getAssessments()) {
            PerformanceReviewDto.TeamLeadAssessmentRequest singleRequest = new PerformanceReviewDto.TeamLeadAssessmentRequest();
            singleRequest.setEmployeeId(assessment.getEmployeeId());
            singleRequest.setQuarter(request.getQuarter());
            singleRequest.setReviewYear(request.getReviewYear());
            singleRequest.setTechnicalScore(assessment.getTechnicalScore());
            singleRequest.setBehavioralScore(assessment.getBehavioralScore());
            singleRequest.setCultureFitScore(assessment.getCultureFitScore());
            singleRequest.setGrowthLearningScore(assessment.getGrowthLearningScore());
            singleRequest.setTechnicalComments(assessment.getTechnicalComments());
            singleRequest.setBehavioralComments(assessment.getBehavioralComments());
            singleRequest.setCultureFitComments(assessment.getCultureFitComments());
            singleRequest.setGrowthLearningComments(assessment.getGrowthLearningComments());
            singleRequest.setStrengths(assessment.getStrengths());
            singleRequest.setAreasForImprovement(assessment.getAreasForImprovement());
            singleRequest.setSubmitForApproval(request.isSubmitForApproval());

            try {
                responses.add(submitAssessment(teamLeadId, singleRequest));
            } catch (Exception e) {
                // Log error but continue with other assessments
                System.err.println("Failed to process assessment for employee " + assessment.getEmployeeId() + ": " + e.getMessage());
            }
        }

        return responses;
    }

    /**
     * Get all reviews for a specific quarter (for admin dashboard)
     */
    public List<PerformanceReviewDto.ReviewResponse> getQuarterlyReviews(String quarter, Integer year) {
        List<PerformanceReview> reviews = reviewRepository.findByQuarterAndReviewYearOrderByCreatedAtDesc(quarter, year);
        return reviews.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    /**
     * Get reviews submitted by a specific team lead
     */
    public List<PerformanceReviewDto.ReviewResponse> getTeamLeadReviews(UUID teamLeadId, String quarter, Integer year) {
        List<PerformanceReview> reviews = reviewRepository.findByReviewerIdAndQuarterAndReviewYear(teamLeadId, quarter, year);
        return reviews.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    /**
     * Get pending reviews awaiting approval (for admin)
     */
    public List<PerformanceReviewDto.ReviewResponse> getPendingReviews(String quarter, Integer year) {
        List<PerformanceReview> reviews = reviewRepository.findByStatusAndQuarterAndReviewYearOrderBySubmittedAtDesc("submitted", quarter, year);
        return reviews.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    /**
     * Admin approves a submitted review
     */
    @Transactional
    public PerformanceReviewDto.ReviewResponse approveReview(Long reviewId, UUID adminId) {
        PerformanceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (!"submitted".equals(review.getStatus())) {
            throw new RuntimeException("Only submitted reviews can be approved");
        }

        review.setStatus("approved");
        review.setApprovedAt(OffsetDateTime.now());

        PerformanceReview saved = reviewRepository.save(review);
        return mapToResponseWithLookup(saved);
    }

    /**
     * Get review summary statistics
     */
    public PerformanceReviewDto.ReviewSummary getReviewSummary(String quarter, Integer year) {
        PerformanceReviewDto.ReviewSummary summary = new PerformanceReviewDto.ReviewSummary();
        summary.setQuarter(quarter);
        summary.setReviewYear(year);

        long submitted = reviewRepository.countByQuarterAndYearAndStatus(quarter, year, "submitted");
        long approved = reviewRepository.countByQuarterAndYearAndStatus(quarter, year, "approved");
        long draft = reviewRepository.countByQuarterAndYearAndStatus(quarter, year, "draft");

        long total = submitted + approved + draft;
        summary.setTotalReviews(total);
        summary.setSubmittedCount(submitted);
        summary.setApprovedCount(approved);
        summary.setPendingCount(draft);

        if (total > 0) {
            summary.setCompletionPercentage((int) ((approved * 100) / total));
        }

        return summary;
    }

    /**
     * Get employee's review history
     */
    public List<PerformanceReviewDto.ReviewResponse> getEmployeeReviewHistory(UUID employeeId) {
        List<PerformanceReview> reviews = reviewRepository.findByEmployeeIdOrderByReviewYearDescQuarterDesc(employeeId);
        return reviews.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    // Helper methods
    private PerformanceReviewDto.ReviewResponse mapToResponse(PerformanceReview review, Profile employee, Profile reviewer) {
        PerformanceReviewDto.ReviewResponse response = new PerformanceReviewDto.ReviewResponse();
        response.setId(review.getId());
        response.setEmployeeId(review.getEmployeeId().toString());
        response.setEmployeeName(employee != null ? employee.getFullName() : null);
        response.setDepartment(employee != null ? employee.getDepartment() : null);
        response.setQuarter(review.getQuarter());
        response.setReviewYear(review.getReviewYear());

        response.setTechnicalScore(review.getTechnicalScore());
        response.setBehavioralScore(review.getBehavioralScore());
        response.setCultureFitScore(review.getCultureFitScore());
        response.setGrowthLearningScore(review.getGrowthLearningScore());

        response.setQuarterlyScore(review.getQuarterlyScore());
        response.setQuarterlyGpa(review.getQuarterlyGpa());
        response.setGrade(calculateGrade(review.getQuarterlyGpa()));

        response.setLeadershipScore(review.getLeadershipScore());
        response.setIsTeamLead(employee != null ? employee.getIsTeamLead() : false);

        response.setTechnicalComments(review.getTechnicalComments());
        response.setBehavioralComments(review.getBehavioralComments());
        response.setCultureFitComments(review.getCultureFitComments());
        response.setGrowthLearningComments(review.getGrowthLearningComments());
        response.setStrengths(review.getStrengths());
        response.setAreasForImprovement(review.getAreasForImprovement());

        response.setStatus(review.getStatus());
        response.setReviewerName(reviewer != null ? reviewer.getFullName() : null);
        response.setSubmittedAt(review.getSubmittedAt() != null ? review.getSubmittedAt().toString() : null);
        response.setApprovedAt(review.getApprovedAt() != null ? review.getApprovedAt().toString() : null);

        return response;
    }

    private PerformanceReviewDto.ReviewResponse mapToResponseWithLookup(PerformanceReview review) {
        Profile employee = profileRepository.findById(review.getEmployeeId()).orElse(null);
        Profile reviewer = review.getReviewerId() != null
                ? profileRepository.findById(review.getReviewerId()).orElse(null)
                : null;
        return mapToResponse(review, employee, reviewer);
    }

    private String calculateGrade(BigDecimal gpa) {
        if (gpa == null) return null;
        double g = gpa.doubleValue();
        if (g >= 4.30) return "A";
        if (g >= 3.80) return "B";
        if (g >= 3.30) return "C";
        if (g >= 2.50) return "D";
        return "F";
    }
}
