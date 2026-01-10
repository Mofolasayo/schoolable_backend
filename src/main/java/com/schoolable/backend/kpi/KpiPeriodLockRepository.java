package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KpiPeriodLockRepository extends JpaRepository<KpiPeriodLock, UUID> {
    @Query("SELECT k FROM KpiPeriodLock k WHERE k.kpiType = :type AND k.quarter = :quarter AND k.year = :year AND k.isLocked = true AND ((:teamLeadId IS NULL AND k.teamLeadId IS NULL) OR k.teamLeadId = :teamLeadId) AND ((:department IS NULL AND k.department IS NULL) OR k.department = :department)")
    Optional<KpiPeriodLock> findActiveLock(@Param("type") String type, @Param("quarter") String quarter, @Param("year") Integer year, @Param("teamLeadId") UUID teamLeadId, @Param("department") String department);
}
