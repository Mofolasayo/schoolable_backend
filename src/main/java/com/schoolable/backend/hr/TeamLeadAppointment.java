package com.schoolable.backend.hr;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Team Lead appointment tracking.
 * Based on Allpro Team Lead Policy.
 */
@Entity
@Table(name = "team_lead_appointments")
public class TeamLeadAppointment {
    
    public static final String STATUS_ACTING = "acting";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_ENDED = "ended";
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "appointed_at", nullable = false)
    private OffsetDateTime appointedAt;
    
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
    
    @Column(length = 50, nullable = false)
    private String status = STATUS_ACTING;
    
    @Column(length = 100)
    private String department;
    
    @Column(name = "team_name", length = 200)
    private String teamName;
    
    @Column(name = "team_size")
    private Integer teamSize;
    
    @Column(name = "review_cycles_completed")
    private Integer reviewCyclesCompleted = 0;
    
    @Column(name = "cgpa_at_appointment", precision = 3, scale = 2)
    private BigDecimal cgpaAtAppointment;
    
    @Column(name = "current_cgpa", precision = 3, scale = 2)
    private BigDecimal currentCgpa;
    
    @Column(columnDefinition = "JSONB DEFAULT '[]'")
    private String perks; // JSON array: ["workspace", "data_allowance", "retreat", etc.]
    
    @Column(name = "ended_at")
    private OffsetDateTime endedAt;
    
    @Column(name = "end_reason", columnDefinition = "TEXT")
    private String endReason;
    
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
    
    /**
     * Get months as team lead.
     */
    public long getMonthsAsTeamLead() {
        OffsetDateTime endTime = endedAt != null ? endedAt : OffsetDateTime.now();
        return java.time.temporal.ChronoUnit.MONTHS.between(appointedAt, endTime);
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    
    public OffsetDateTime getAppointedAt() { return appointedAt; }
    public void setAppointedAt(OffsetDateTime appointedAt) { this.appointedAt = appointedAt; }
    
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    
    public Integer getTeamSize() { return teamSize; }
    public void setTeamSize(Integer teamSize) { this.teamSize = teamSize; }
    
    public Integer getReviewCyclesCompleted() { return reviewCyclesCompleted; }
    public void setReviewCyclesCompleted(Integer reviewCyclesCompleted) { this.reviewCyclesCompleted = reviewCyclesCompleted; }
    
    public BigDecimal getCgpaAtAppointment() { return cgpaAtAppointment; }
    public void setCgpaAtAppointment(BigDecimal cgpaAtAppointment) { this.cgpaAtAppointment = cgpaAtAppointment; }
    
    public BigDecimal getCurrentCgpa() { return currentCgpa; }
    public void setCurrentCgpa(BigDecimal currentCgpa) { this.currentCgpa = currentCgpa; }
    
    public String getPerks() { return perks; }
    public void setPerks(String perks) { this.perks = perks; }
    
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
