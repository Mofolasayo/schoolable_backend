package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface KpiHistoryRepository extends JpaRepository<KpiHistory, UUID> {

    /**
     * Find all history for a specific KPI
     */
    List<KpiHistory> findByKpiIdOrderByChangedAtDesc(UUID kpiId);

    /**
     * Find history by KPI type
     */
    List<KpiHistory> findByKpiTypeOrderByChangedAtDesc(String kpiType);

    /**
     * Find history by who made the change
     */
    List<KpiHistory> findByChangedByOrderByChangedAtDesc(UUID changedBy);

    /**
     * Find history in a date range
     */
    @Query("SELECT h FROM KpiHistory h WHERE h.changedAt BETWEEN :start AND :end ORDER BY h.changedAt DESC")
    List<KpiHistory> findByDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * Count changes for a specific KPI
     */
    long countByKpiId(UUID kpiId);
}
