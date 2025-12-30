package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a quarterly performance review.
 * Contains 4 core pillars (25% each = 100% Aura) plus separate leadership score for team leads.
 */
@Entity
@Table(name = "performance_reviews")
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String quarter; // 'Q1', 'Q2', 'Q3', 'Q4'

    @Column(name = "review_year", nullable = false)
    private Integer reviewYear;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    // 4 Core Pillars (25% each = 100% Aura)
    @Column(name = "technical_score", precision = 5, scale = 2)
    private BigDecimal technicalScore;

    @Column(name = "behavioral_score", precision = 5, scale = 2)
    private BigDecimal behavioralScore;

    @Column(name = "culture_fit_score", precision = 5, scale = 2)
    private BigDecimal cultureFitScore;

    @Column(name = "growth_learning_score", precision = 5, scale = 2)
    private BigDecimal growthLearningScore;

    // Leadership Score (Team Leads ONLY - separate from Aura)
    @Column(name = "leadership_score", precision = 5, scale = 2)
    private BigDecimal leadershipScore;

    @Column(name = "is_team_lead_review")
    private Boolean isTeamLeadReview = false;

    // Calculated fields (auto-generated in DB)
    @Column(name = "quarterly_score", precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal quarterlyScore;

    @Column(name = "quarterly_gpa", precision = 3, scale = 2, insertable = false, updatable = false)
    private BigDecimal quarterlyGpa;

    // Comments for each pillar
    @Column(name = "technical_comments", columnDefinition = "TEXT")
    private String technicalComments;

    @Column(name = "behavioral_comments", columnDefinition = "TEXT")
    private String behavioralComments;

    @Column(name = "culture_fit_comments", columnDefinition = "TEXT")
    private String cultureFitComments;

    @Column(name = "growth_learning_comments", columnDefinition = "TEXT")
    private String growthLearningComments;

    @Column(name = "leadership_comments", columnDefinition = "TEXT")
    private String leadershipComments;

    // Overall feedback
    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "areas_for_improvement", columnDefinition = "TEXT")
    private String areasForImprovement;

    // Review metadata
    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column
    private String status = "draft"; // draft, submitted, approved, published

    // Timestamps
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getReviewYear() { return reviewYear; }
    public void setReviewYear(Integer reviewYear) { this.reviewYear = reviewYear; }

    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }

    public BigDecimal getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = technicalScore; }

    public BigDecimal getBehavioralScore() { return behavioralScore; }
    public void setBehavioralScore(BigDecimal behavioralScore) { this.behavioralScore = behavioralScore; }

    public BigDecimal getCultureFitScore() { return cultureFitScore; }
    public void setCultureFitScore(BigDecimal cultureFitScore) { this.cultureFitScore = cultureFitScore; }

    public BigDecimal getGrowthLearningScore() { return growthLearningScore; }
    public void setGrowthLearningScore(BigDecimal growthLearningScore) { this.growthLearningScore = growthLearningScore; }

    public BigDecimal getLeadershipScore() { return leadershipScore; }
    public void setLeadershipScore(BigDecimal leadershipScore) { this.leadershipScore = leadershipScore; }

    public Boolean getIsTeamLeadReview() { return isTeamLeadReview; }
    public void setIsTeamLeadReview(Boolean isTeamLeadReview) { this.isTeamLeadReview = isTeamLeadReview; }

    public BigDecimal getQuarterlyScore() { return quarterlyScore; }
    public BigDecimal getQuarterlyGpa() { return quarterlyGpa; }

    public String getTechnicalComments() { return technicalComments; }
    public void setTechnicalComments(String technicalComments) { this.technicalComments = technicalComments; }

    public String getBehavioralComments() { return behavioralComments; }
    public void setBehavioralComments(String behavioralComments) { this.behavioralComments = behavioralComments; }

    public String getCultureFitComments() { return cultureFitComments; }
    public void setCultureFitComments(String cultureFitComments) { this.cultureFitComments = cultureFitComments; }

    public String getGrowthLearningComments() { return growthLearningComments; }
    public void setGrowthLearningComments(String growthLearningComments) { this.growthLearningComments = growthLearningComments; }

    public String getLeadershipComments() { return leadershipComments; }
    public void setLeadershipComments(String leadershipComments) { this.leadershipComments = leadershipComments; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    public String getAreasForImprovement() { return areasForImprovement; }
    public void setAreasForImprovement(String areasForImprovement) { this.areasForImprovement = areasForImprovement; }

    public UUID getReviewerId() { return reviewerId; }
    public void setReviewerId(UUID reviewerId) { this.reviewerId = reviewerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
}
