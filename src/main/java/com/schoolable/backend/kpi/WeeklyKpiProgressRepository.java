package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyKpiProgressRepository extends JpaRepository<WeeklyKpiProgress, UUID> {

    // Find progress for a specific KPI in a specific week
    Optional<WeeklyKpiProgress> findByKpiIdAndWeekNumberAndYear(UUID kpiId, Integer weekNumber, Integer year);

    // Find all progress for a KPI
    List<WeeklyKpiProgress> findByKpiIdOrderByWeekNumberDesc(UUID kpiId);

    List<WeeklyKpiProgress> findByKpiIdAndYearOrderByWeekNumberDesc(UUID kpiId, Integer year);

    // Find all progress for a week
    List<WeeklyKpiProgress> findByReportedByAndWeekNumberAndYear(UUID reportedBy, Integer weekNumber, Integer year);

    // Get cumulative progress for a KPI
    @Query("SELECT SUM(p.achievedValue) FROM WeeklyKpiProgress p WHERE p.kpiId = :kpiId AND p.year = :year")
    Double sumAchievedValueByKpiIdAndYear(@Param("kpiId") UUID kpiId, @Param("year") Integer year);

    // Get all progress for a team lead's KPIs in a quarter
    @Query("SELECT p FROM WeeklyKpiProgress p WHERE p.kpiId IN " +
           "(SELECT k.id FROM TeamKpi k WHERE k.teamLeadId = :teamLeadId AND k.quarter = :quarter AND k.year = :year) " +
           "ORDER BY p.weekNumber DESC")
    List<WeeklyKpiProgress> findAllByTeamLeadAndQuarter(@Param("teamLeadId") UUID teamLeadId,
                                                        @Param("quarter") String quarter,
                                                        @Param("year") Integer year);
}
