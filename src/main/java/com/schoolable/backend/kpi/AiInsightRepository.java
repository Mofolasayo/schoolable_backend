package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {

    // Get latest weekly insight for a team
    Optional<AiInsight> findFirstByTeamLeadIdAndInsightTypeOrderByGeneratedAtDesc(
        UUID teamLeadId, String insightType);

    // Get specific week's insight
    Optional<AiInsight> findByTeamLeadIdAndInsightTypeAndWeekNumberAndYear(
        UUID teamLeadId, String insightType, Integer weekNumber, Integer year);

    // Get quarterly insight
    Optional<AiInsight> findByTeamLeadIdAndInsightTypeAndQuarterAndYear(
        UUID teamLeadId, String insightType, String quarter, Integer year);

    // Get all insights for a team (for history)
    List<AiInsight> findByTeamLeadIdOrderByGeneratedAtDesc(UUID teamLeadId);

    // Get insights for a department (for team members)
    List<AiInsight> findByDepartmentAndInsightTypeOrderByGeneratedAtDesc(String department, String insightType);

    // Get latest insight by department for team members
    Optional<AiInsight> findFirstByDepartmentAndInsightTypeOrderByGeneratedAtDesc(String department, String insightType);

    // Get all insights for a specific week (admin view)
    @Query("SELECT a FROM AiInsight a WHERE a.insightType = 'WEEKLY' AND a.weekNumber = :weekNumber AND a.year = :year ORDER BY a.department")
    List<AiInsight> findAllWeeklyInsights(@Param("weekNumber") Integer weekNumber, @Param("year") Integer year);
}
