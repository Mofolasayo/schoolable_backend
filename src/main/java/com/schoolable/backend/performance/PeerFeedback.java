package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity for peer-to-peer feedback.
 * Used for calculating sub-metrics that require peer input.
 * 
 * Rating fields cover:
 * - Behavioral: adaptability
 * - Culture Fit: company values, accountability
 * - Growth: openness to feedback
 * - Leadership (for team leads): org guidance, people leadership, influence
 */
@Entity
@Table(name = "peer_feedback")
public class PeerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_employee_id", nullable = false)
    private UUID fromEmployeeId;

    @Column(name = "to_employee_id", nullable = false)
    private UUID toEmployeeId;

    @Column(nullable = false)
    private String quarter; // Q1, Q2, Q3, Q4

    @Column(nullable = false)
    private Integer year;

    // ============ EXISTING RATINGS ============
    @Column(name = "support_rating", nullable = false)
    private Integer supportRating; // 1-5 (Teamwork)

    @Column(name = "collaboration_rating")
    private Integer collaborationRating; // 1-5

    @Column(name = "communication_rating")
    private Integer communicationRating; // 1-5

    // ============ NEW: BEHAVIORAL SUB-METRICS ============
    @Column(name = "adaptability_rating")
    private Integer adaptabilityRating; // 1-5

    // ============ NEW: CULTURE FIT SUB-METRICS ============
    @Column(name = "values_rating")
    private Integer valuesRating; // 1-5 (Adherence to Company Values)

    @Column(name = "accountability_rating")
    private Integer accountabilityRating; // 1-5 (Accountability & Ownership)

    // ============ NEW: GROWTH SUB-METRICS ============
    @Column(name = "feedback_rating")
    private Integer feedbackRating; // 1-5 (Openness to Feedback)

    // ============ NEW: LEADERSHIP SUB-METRICS (for rating team leads) ============
    @Column(name = "org_guidance_rating")
    private Integer orgGuidanceRating; // 1-5 (Organizational Guidance)

    @Column(name = "people_culture_rating")
    private Integer peopleCultureRating; // 1-5 (People & Culture Leadership)

    @Column(name = "influence_rating")
    private Integer influenceRating; // 1-5 (Leadership Influence)

    // ============ TEXT FEEDBACK ============
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "areas_for_improvement", columnDefinition = "TEXT")
    private String areasForImprovement;

    @Column(name = "is_anonymous")
    private Boolean isAnonymous = true;

    private String status = "submitted"; // draft, submitted

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // ============ GETTERS AND SETTERS ============
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getFromEmployeeId() { return fromEmployeeId; }
    public void setFromEmployeeId(UUID fromEmployeeId) { this.fromEmployeeId = fromEmployeeId; }

    public UUID getToEmployeeId() { return toEmployeeId; }
    public void setToEmployeeId(UUID toEmployeeId) { this.toEmployeeId = toEmployeeId; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getSupportRating() { return supportRating; }
    public void setSupportRating(Integer supportRating) { this.supportRating = supportRating; }

    public Integer getCollaborationRating() { return collaborationRating; }
    public void setCollaborationRating(Integer collaborationRating) { this.collaborationRating = collaborationRating; }

    public Integer getCommunicationRating() { return communicationRating; }
    public void setCommunicationRating(Integer communicationRating) { this.communicationRating = communicationRating; }

    public Integer getAdaptabilityRating() { return adaptabilityRating; }
    public void setAdaptabilityRating(Integer adaptabilityRating) { this.adaptabilityRating = adaptabilityRating; }

    public Integer getValuesRating() { return valuesRating; }
    public void setValuesRating(Integer valuesRating) { this.valuesRating = valuesRating; }

    public Integer getAccountabilityRating() { return accountabilityRating; }
    public void setAccountabilityRating(Integer accountabilityRating) { this.accountabilityRating = accountabilityRating; }

    public Integer getFeedbackRating() { return feedbackRating; }
    public void setFeedbackRating(Integer feedbackRating) { this.feedbackRating = feedbackRating; }

    public Integer getOrgGuidanceRating() { return orgGuidanceRating; }
    public void setOrgGuidanceRating(Integer orgGuidanceRating) { this.orgGuidanceRating = orgGuidanceRating; }

    public Integer getPeopleCultureRating() { return peopleCultureRating; }
    public void setPeopleCultureRating(Integer peopleCultureRating) { this.peopleCultureRating = peopleCultureRating; }

    public Integer getInfluenceRating() { return influenceRating; }
    public void setInfluenceRating(Integer influenceRating) { this.influenceRating = influenceRating; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    public String getAreasForImprovement() { return areasForImprovement; }
    public void setAreasForImprovement(String areasForImprovement) { this.areasForImprovement = areasForImprovement; }

    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}

