package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin Team Lead Rating Entity
 * Super Admin rates Team Leads weekly on their leadership and management.
 * These ratings contribute to the Team Lead's own Aura score.
 */
@Entity
@Table(name = "admin_team_lead_ratings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"team_lead_id", "week_number", "year"})
})
public class AdminTeamLeadRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_lead_id", nullable = false)
    private UUID teamLeadId;

    @Column(name = "rated_by_id", nullable = false)
    private UUID ratedById;

    // Period
    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    // Scores (1-5 scale)
    @Column(name = "leadership_score")
    private Integer leadershipScore;

    @Column(name = "team_management_score")
    private Integer teamManagementScore;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @Column(name = "results_delivery_score")
    private Integer resultsDeliveryScore;

    @Column(name = "culture_champion_score")
    private Integer cultureChampionScore;

    // Notes
    @Column(name = "leadership_notes", columnDefinition = "TEXT")
    private String leadershipNotes;

    @Column(name = "areas_of_strength", columnDefinition = "TEXT")
    private String areasOfStrength;

    @Column(name = "areas_for_improvement", columnDefinition = "TEXT")
    private String areasForImprovement;

    @Column(length = 20)
    private String status = "submitted";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Helper method to get average score
    public Double getAverageScore() {
        int count = 0;
        int total = 0;
        
        if (leadershipScore != null) { total += leadershipScore; count++; }
        if (teamManagementScore != null) { total += teamManagementScore; count++; }
        if (communicationScore != null) { total += communicationScore; count++; }
        if (resultsDeliveryScore != null) { total += resultsDeliveryScore; count++; }
        if (cultureChampionScore != null) { total += cultureChampionScore; count++; }
        
        return count > 0 ? (double) total / count : null;
    }

    // Constructors
    public AdminTeamLeadRating() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTeamLeadId() { return teamLeadId; }
    public void setTeamLeadId(UUID teamLeadId) { this.teamLeadId = teamLeadId; }

    public UUID getRatedById() { return ratedById; }
    public void setRatedById(UUID ratedById) { this.ratedById = ratedById; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }

    public LocalDate getWeekEndDate() { return weekEndDate; }
    public void setWeekEndDate(LocalDate weekEndDate) { this.weekEndDate = weekEndDate; }

    public Integer getLeadershipScore() { return leadershipScore; }
    public void setLeadershipScore(Integer leadershipScore) { this.leadershipScore = leadershipScore; }

    public Integer getTeamManagementScore() { return teamManagementScore; }
    public void setTeamManagementScore(Integer teamManagementScore) { this.teamManagementScore = teamManagementScore; }

    public Integer getCommunicationScore() { return communicationScore; }
    public void setCommunicationScore(Integer communicationScore) { this.communicationScore = communicationScore; }

    public Integer getResultsDeliveryScore() { return resultsDeliveryScore; }
    public void setResultsDeliveryScore(Integer resultsDeliveryScore) { this.resultsDeliveryScore = resultsDeliveryScore; }

    public Integer getCultureChampionScore() { return cultureChampionScore; }
    public void setCultureChampionScore(Integer cultureChampionScore) { this.cultureChampionScore = cultureChampionScore; }

    public String getLeadershipNotes() { return leadershipNotes; }
    public void setLeadershipNotes(String leadershipNotes) { this.leadershipNotes = leadershipNotes; }

    public String getAreasOfStrength() { return areasOfStrength; }
    public void setAreasOfStrength(String areasOfStrength) { this.areasOfStrength = areasOfStrength; }

    public String getAreasForImprovement() { return areasForImprovement; }
    public void setAreasForImprovement(String areasForImprovement) { this.areasForImprovement = areasForImprovement; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
