package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kpi_period_locks")
public class KpiPeriodLock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kpi_type", nullable = false, length = 20)
    private String kpiType;

    private String department;

    @Column(name = "team_lead_id")
    private UUID teamLeadId;

    @Column(nullable = false, length = 10)
    private String quarter;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt = OffsetDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "is_locked")
    private Boolean isLocked = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getKpiType() { return kpiType; }
    public void setKpiType(String kpiType) { this.kpiType = kpiType; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public UUID getTeamLeadId() { return teamLeadId; }
    public void setTeamLeadId(UUID teamLeadId) { this.teamLeadId = teamLeadId; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public UUID getLockedBy() { return lockedBy; }
    public void setLockedBy(UUID lockedBy) { this.lockedBy = lockedBy; }

    public OffsetDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(OffsetDateTime lockedAt) { this.lockedAt = lockedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }
}
