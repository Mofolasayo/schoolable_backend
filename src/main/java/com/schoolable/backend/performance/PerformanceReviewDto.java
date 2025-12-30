package com.schoolable.backend.performance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs for Performance Review API
 */
public class PerformanceReviewDto {

    /**
     * Request DTO for team lead to submit a performance review for a team member
     */
    public static class TeamLeadAssessmentRequest {
        
        @NotNull(message = "Employee ID is required")
        private String employeeId;

        @NotBlank(message = "Quarter is required")
        @Pattern(regexp = "^Q[1-4]$", message = "Quarter must be Q1, Q2, Q3, or Q4")
        private String quarter;

        @NotNull(message = "Review year is required")
        @Min(value = 2024, message = "Year must be 2024 or later")
        private Integer reviewYear;

        // 4 Core Pillar Scores (each 0-100)
        @NotNull(message = "Technical score is required")
        @DecimalMin(value = "0", message = "Score must be at least 0")
        @DecimalMax(value = "100", message = "Score cannot exceed 100")
        private BigDecimal technicalScore;

        @NotNull(message = "Behavioral score is required")
        @DecimalMin(value = "0", message = "Score must be at least 0")
        @DecimalMax(value = "100", message = "Score cannot exceed 100")
        private BigDecimal behavioralScore;

        @NotNull(message = "Culture fit score is required")
        @DecimalMin(value = "0", message = "Score must be at least 0")
        @DecimalMax(value = "100", message = "Score cannot exceed 100")
        private BigDecimal cultureFitScore;

        @NotNull(message = "Growth & Learning score is required")
        @DecimalMin(value = "0", message = "Score must be at least 0")
        @DecimalMax(value = "100", message = "Score cannot exceed 100")
        private BigDecimal growthLearningScore;

        // Comments for each pillar
        private String technicalComments;
        private String behavioralComments;
        private String cultureFitComments;
        private String growthLearningComments;

        // Overall feedback
        private String strengths;
        private String areasForImprovement;
        private String overallComments;

        // Whether to submit or save as draft
        private boolean submitForApproval = false;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getQuarter() { return quarter; }
        public void setQuarter(String quarter) { this.quarter = quarter; }

        public Integer getReviewYear() { return reviewYear; }
        public void setReviewYear(Integer reviewYear) { this.reviewYear = reviewYear; }

        public BigDecimal getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = technicalScore; }

        public BigDecimal getBehavioralScore() { return behavioralScore; }
        public void setBehavioralScore(BigDecimal behavioralScore) { this.behavioralScore = behavioralScore; }

        public BigDecimal getCultureFitScore() { return cultureFitScore; }
        public void setCultureFitScore(BigDecimal cultureFitScore) { this.cultureFitScore = cultureFitScore; }

        public BigDecimal getGrowthLearningScore() { return growthLearningScore; }
        public void setGrowthLearningScore(BigDecimal growthLearningScore) { this.growthLearningScore = growthLearningScore; }

        public String getTechnicalComments() { return technicalComments; }
        public void setTechnicalComments(String technicalComments) { this.technicalComments = technicalComments; }

        public String getBehavioralComments() { return behavioralComments; }
        public void setBehavioralComments(String behavioralComments) { this.behavioralComments = behavioralComments; }

        public String getCultureFitComments() { return cultureFitComments; }
        public void setCultureFitComments(String cultureFitComments) { this.cultureFitComments = cultureFitComments; }

        public String getGrowthLearningComments() { return growthLearningComments; }
        public void setGrowthLearningComments(String growthLearningComments) { this.growthLearningComments = growthLearningComments; }

        public String getStrengths() { return strengths; }
        public void setStrengths(String strengths) { this.strengths = strengths; }

        public String getAreasForImprovement() { return areasForImprovement; }
        public void setAreasForImprovement(String areasForImprovement) { this.areasForImprovement = areasForImprovement; }

        public String getOverallComments() { return overallComments; }
        public void setOverallComments(String overallComments) { this.overallComments = overallComments; }

        public boolean isSubmitForApproval() { return submitForApproval; }
        public void setSubmitForApproval(boolean submitForApproval) { this.submitForApproval = submitForApproval; }
    }

    /**
     * Request DTO for batch upload of team reviews (multiple team members at once)
     */
    public static class BatchTeamAssessmentRequest {

        @NotBlank(message = "Quarter is required")
        @Pattern(regexp = "^Q[1-4]$", message = "Quarter must be Q1, Q2, Q3, or Q4")
        private String quarter;

        @NotNull(message = "Review year is required")
        private Integer reviewYear;

        @NotEmpty(message = "At least one assessment is required")
        private List<TeamMemberAssessment> assessments;

        private boolean submitForApproval = false;

        // Getters and Setters
        public String getQuarter() { return quarter; }
        public void setQuarter(String quarter) { this.quarter = quarter; }

        public Integer getReviewYear() { return reviewYear; }
        public void setReviewYear(Integer reviewYear) { this.reviewYear = reviewYear; }

        public List<TeamMemberAssessment> getAssessments() { return assessments; }
        public void setAssessments(List<TeamMemberAssessment> assessments) { this.assessments = assessments; }

        public boolean isSubmitForApproval() { return submitForApproval; }
        public void setSubmitForApproval(boolean submitForApproval) { this.submitForApproval = submitForApproval; }
    }

    /**
     * Individual team member assessment within a batch
     */
    public static class TeamMemberAssessment {

        @NotNull(message = "Employee ID is required")
        private String employeeId;

        private String employeeName; // For reference, not stored

        @NotNull @DecimalMin("0") @DecimalMax("100")
        private BigDecimal technicalScore;

        @NotNull @DecimalMin("0") @DecimalMax("100")
        private BigDecimal behavioralScore;

        @NotNull @DecimalMin("0") @DecimalMax("100")
        private BigDecimal cultureFitScore;

        @NotNull @DecimalMin("0") @DecimalMax("100")
        private BigDecimal growthLearningScore;

        private String technicalComments;
        private String behavioralComments;
        private String cultureFitComments;
        private String growthLearningComments;
        private String strengths;
        private String areasForImprovement;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public BigDecimal getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = technicalScore; }

        public BigDecimal getBehavioralScore() { return behavioralScore; }
        public void setBehavioralScore(BigDecimal behavioralScore) { this.behavioralScore = behavioralScore; }

        public BigDecimal getCultureFitScore() { return cultureFitScore; }
        public void setCultureFitScore(BigDecimal cultureFitScore) { this.cultureFitScore = cultureFitScore; }

        public BigDecimal getGrowthLearningScore() { return growthLearningScore; }
        public void setGrowthLearningScore(BigDecimal growthLearningScore) { this.growthLearningScore = growthLearningScore; }

        public String getTechnicalComments() { return technicalComments; }
        public void setTechnicalComments(String technicalComments) { this.technicalComments = technicalComments; }

        public String getBehavioralComments() { return behavioralComments; }
        public void setBehavioralComments(String behavioralComments) { this.behavioralComments = behavioralComments; }

        public String getCultureFitComments() { return cultureFitComments; }
        public void setCultureFitComments(String cultureFitComments) { this.cultureFitComments = cultureFitComments; }

        public String getGrowthLearningComments() { return growthLearningComments; }
        public void setGrowthLearningComments(String growthLearningComments) { this.growthLearningComments = growthLearningComments; }

        public String getStrengths() { return strengths; }
        public void setStrengths(String strengths) { this.strengths = strengths; }

        public String getAreasForImprovement() { return areasForImprovement; }
        public void setAreasForImprovement(String areasForImprovement) { this.areasForImprovement = areasForImprovement; }
    }

    /**
     * Response DTO for performance review
     */
    public static class ReviewResponse {
        private Long id;
        private String employeeId;
        private String employeeName;
        private String department;
        private String quarter;
        private Integer reviewYear;

        // Pillar scores
        private BigDecimal technicalScore;
        private BigDecimal behavioralScore;
        private BigDecimal cultureFitScore;
        private BigDecimal growthLearningScore;

        // Calculated
        private BigDecimal quarterlyScore; // Aura %
        private BigDecimal quarterlyGpa;
        private String grade;

        // Leadership (team leads only)
        private BigDecimal leadershipScore;
        private Boolean isTeamLead;

        // Comments
        private String technicalComments;
        private String behavioralComments;
        private String cultureFitComments;
        private String growthLearningComments;
        private String strengths;
        private String areasForImprovement;

        // Metadata
        private String status;
        private String reviewerName;
        private String submittedAt;
        private String approvedAt;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getQuarter() { return quarter; }
        public void setQuarter(String quarter) { this.quarter = quarter; }

        public Integer getReviewYear() { return reviewYear; }
        public void setReviewYear(Integer reviewYear) { this.reviewYear = reviewYear; }

        public BigDecimal getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = technicalScore; }

        public BigDecimal getBehavioralScore() { return behavioralScore; }
        public void setBehavioralScore(BigDecimal behavioralScore) { this.behavioralScore = behavioralScore; }

        public BigDecimal getCultureFitScore() { return cultureFitScore; }
        public void setCultureFitScore(BigDecimal cultureFitScore) { this.cultureFitScore = cultureFitScore; }

        public BigDecimal getGrowthLearningScore() { return growthLearningScore; }
        public void setGrowthLearningScore(BigDecimal growthLearningScore) { this.growthLearningScore = growthLearningScore; }

        public BigDecimal getQuarterlyScore() { return quarterlyScore; }
        public void setQuarterlyScore(BigDecimal quarterlyScore) { this.quarterlyScore = quarterlyScore; }

        public BigDecimal getQuarterlyGpa() { return quarterlyGpa; }
        public void setQuarterlyGpa(BigDecimal quarterlyGpa) { this.quarterlyGpa = quarterlyGpa; }

        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }

        public BigDecimal getLeadershipScore() { return leadershipScore; }
        public void setLeadershipScore(BigDecimal leadershipScore) { this.leadershipScore = leadershipScore; }

        public Boolean getIsTeamLead() { return isTeamLead; }
        public void setIsTeamLead(Boolean isTeamLead) { this.isTeamLead = isTeamLead; }

        public String getTechnicalComments() { return technicalComments; }
        public void setTechnicalComments(String technicalComments) { this.technicalComments = technicalComments; }

        public String getBehavioralComments() { return behavioralComments; }
        public void setBehavioralComments(String behavioralComments) { this.behavioralComments = behavioralComments; }

        public String getCultureFitComments() { return cultureFitComments; }
        public void setCultureFitComments(String cultureFitComments) { this.cultureFitComments = cultureFitComments; }

        public String getGrowthLearningComments() { return growthLearningComments; }
        public void setGrowthLearningComments(String growthLearningComments) { this.growthLearningComments = growthLearningComments; }

        public String getStrengths() { return strengths; }
        public void setStrengths(String strengths) { this.strengths = strengths; }

        public String getAreasForImprovement() { return areasForImprovement; }
        public void setAreasForImprovement(String areasForImprovement) { this.areasForImprovement = areasForImprovement; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getReviewerName() { return reviewerName; }
        public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

        public String getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }

        public String getApprovedAt() { return approvedAt; }
        public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
    }

    /**
     * Summary response for dashboard
     */
    public static class ReviewSummary {
        private String quarter;
        private Integer reviewYear;
        private long totalReviews;
        private long submittedCount;
        private long approvedCount;
        private long pendingCount;
        private double averageGpa;
        private int completionPercentage;

        public String getQuarter() { return quarter; }
        public void setQuarter(String quarter) { this.quarter = quarter; }

        public Integer getReviewYear() { return reviewYear; }
        public void setReviewYear(Integer reviewYear) { this.reviewYear = reviewYear; }

        public long getTotalReviews() { return totalReviews; }
        public void setTotalReviews(long totalReviews) { this.totalReviews = totalReviews; }

        public long getSubmittedCount() { return submittedCount; }
        public void setSubmittedCount(long submittedCount) { this.submittedCount = submittedCount; }

        public long getApprovedCount() { return approvedCount; }
        public void setApprovedCount(long approvedCount) { this.approvedCount = approvedCount; }

        public long getPendingCount() { return pendingCount; }
        public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

        public double getAverageGpa() { return averageGpa; }
        public void setAverageGpa(double averageGpa) { this.averageGpa = averageGpa; }

        public int getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(int completionPercentage) { this.completionPercentage = completionPercentage; }
    }
}
