package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamQuarterlyScoreRepository extends JpaRepository<TeamQuarterlyScore, UUID> {

    // Get score for a specific team and quarter
    Optional<TeamQuarterlyScore> findByTeamLeadIdAndQuarterAndYear(UUID teamLeadId, String quarter, Integer year);

    // Get all team scores for a quarter (super admin view)
    List<TeamQuarterlyScore> findByQuarterAndYearOrderByOverallTeamScoreDesc(String quarter, Integer year);

    // Get all scores for a department
    List<TeamQuarterlyScore> findByDepartmentAndQuarterAndYear(String department, String quarter, Integer year);

    // Get historical scores for a team
    List<TeamQuarterlyScore> findByTeamLeadIdOrderByYearDescQuarterDesc(UUID teamLeadId);

    // Get average score across all teams
    @Query("SELECT AVG(t.overallTeamScore) FROM TeamQuarterlyScore t WHERE t.quarter = :quarter AND t.year = :year")
    Double getAverageScoreByQuarter(@Param("quarter") String quarter, @Param("year") Integer year);

    // Get top performing teams
    @Query("SELECT t FROM TeamQuarterlyScore t WHERE t.quarter = :quarter AND t.year = :year ORDER BY t.overallTeamScore DESC")
    List<TeamQuarterlyScore> findTopTeamsByQuarter(@Param("quarter") String quarter, @Param("year") Integer year);
}
