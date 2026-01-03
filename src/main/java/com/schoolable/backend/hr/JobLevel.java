package com.schoolable.backend.hr;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Job Level entity representing the 14-step cadre system.
 * Based on Allpro Technologies Employment Level Cadre Policy.
 */
@Entity
@Table(name = "job_levels")
public class JobLevel {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "level_number", nullable = false, unique = true)
    private Integer levelNumber;
    
    @Column(nullable = false, length = 100)
    private String title;
    
    @Column(nullable = false)
    private Integer grade; // 1-6 based on pyramid
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "min_years_experience")
    private Integer minYearsExperience;
    
    @Column(name = "max_years_experience")
    private Integer maxYearsExperience;
    
    @Column(name = "is_team_lead_eligible")
    private Boolean isTeamLeadEligible = false;
    
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
    
    // Grade descriptions based on policy pyramid
    public String getGradeDescription() {
        return switch (grade) {
            case 1 -> "Auxiliary & Contract Staff";
            case 2 -> "NYSC, Internship, Mgt Trainees";
            case 3 -> "Junior Executives, Asst. Team Leads, Team Leads";
            case 4 -> "Senior Executives, Senior Managers, Managers";
            case 5 -> "C-Suite Executives";
            case 6 -> "Directors";
            default -> "Unknown";
        };
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public Integer getLevelNumber() { return levelNumber; }
    public void setLevelNumber(Integer levelNumber) { this.levelNumber = levelNumber; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Integer getMinYearsExperience() { return minYearsExperience; }
    public void setMinYearsExperience(Integer minYearsExperience) { this.minYearsExperience = minYearsExperience; }
    
    public Integer getMaxYearsExperience() { return maxYearsExperience; }
    public void setMaxYearsExperience(Integer maxYearsExperience) { this.maxYearsExperience = maxYearsExperience; }
    
    public Boolean getIsTeamLeadEligible() { return isTeamLeadEligible; }
    public void setIsTeamLeadEligible(Boolean isTeamLeadEligible) { this.isTeamLeadEligible = isTeamLeadEligible; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
