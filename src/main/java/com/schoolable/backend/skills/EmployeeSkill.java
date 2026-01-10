package com.schoolable.backend.skills;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Employee Skill Entity
 * Links employees to skills with proficiency levels.
 * Can be self-assessed or verified by managers.
 */
@Entity
@Table(name = "employee_skills", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employee_id", "skill_id"})
})
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "proficiency_level", nullable = false)
    private Integer proficiencyLevel; // 1-5 (Beginner to Expert)

    @Column(name = "is_self_assessed")
    private Boolean isSelfAssessed = true;

    @Column(name = "verified_by")
    private UUID verifiedBy; // Manager who verified the skill

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "years_experience")
    private Double yearsExperience;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // Certifications, projects, etc.

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt; // When skill was last actively used

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Constructors
    public EmployeeSkill() {}

    public EmployeeSkill(UUID employeeId, UUID skillId, Integer proficiencyLevel) {
        this.employeeId = employeeId;
        this.skillId = skillId;
        this.proficiencyLevel = proficiencyLevel;
    }

    // Helper methods
    public String getProficiencyLabel() {
        switch (proficiencyLevel) {
            case 1: return "Beginner";
            case 2: return "Elementary";
            case 3: return "Intermediate";
            case 4: return "Advanced";
            case 5: return "Expert";
            default: return "Unknown";
        }
    }

    public boolean isVerified() {
        return verifiedBy != null;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getSkillId() { return skillId; }
    public void setSkillId(UUID skillId) { this.skillId = skillId; }

    public Integer getProficiencyLevel() { return proficiencyLevel; }
    public void setProficiencyLevel(Integer proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }

    public Boolean getIsSelfAssessed() { return isSelfAssessed; }
    public void setIsSelfAssessed(Boolean isSelfAssessed) { this.isSelfAssessed = isSelfAssessed; }

    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public Double getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Double yearsExperience) { this.yearsExperience = yearsExperience; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
