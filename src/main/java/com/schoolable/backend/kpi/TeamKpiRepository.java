package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamKpiRepository extends JpaRepository<TeamKpi, UUID> {

    // Find all KPIs for a team lead
    List<TeamKpi> findByTeamLeadIdOrderByCreatedAtDesc(UUID teamLeadId);

    // Find active KPIs for a team lead in a specific quarter
    List<TeamKpi> findByTeamLeadIdAndQuarterAndYearAndIsActiveTrue(
        UUID teamLeadId, String quarter, Integer year);

    // Find all KPIs for a department
    List<TeamKpi> findByDepartmentAndQuarterAndYearAndIsActiveTrue(
        String department, String quarter, Integer year);

    // Count total weight for validation
    @Query("SELECT COALESCE(SUM(k.weight), 0) FROM TeamKpi k WHERE k.teamLeadId = :teamLeadId AND k.quarter = :quarter AND k.year = :year AND k.isActive = true")
    Integer sumWeightByTeamLeadAndQuarter(@Param("teamLeadId") UUID teamLeadId, 
                                          @Param("quarter") String quarter, 
                                          @Param("year") Integer year);

    // Find KPIs by department for super admin
    @Query("SELECT k FROM TeamKpi k WHERE k.isActive = true AND k.quarter = :quarter AND k.year = :year ORDER BY k.department, k.name")
    List<TeamKpi> findAllActiveByQuarterAndYear(@Param("quarter") String quarter, @Param("year") Integer year);

    List<TeamKpi> findByAutoProgressEnabledTrueAndQuarterAndYear(String quarter, Integer year);
}
