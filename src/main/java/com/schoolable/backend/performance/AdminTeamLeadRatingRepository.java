package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminTeamLeadRatingRepository extends JpaRepository<AdminTeamLeadRating, Long> {

    // Find by team lead and week
    Optional<AdminTeamLeadRating> findByTeamLeadIdAndWeekNumberAndYear(
        UUID teamLeadId, Integer weekNumber, Integer year);

    // Get all ratings for a team lead in a quarter
    @Query("SELECT r FROM AdminTeamLeadRating r WHERE r.teamLeadId = :teamLeadId AND r.year = :year " +
           "AND r.weekNumber >= :startWeek AND r.weekNumber <= :endWeek ORDER BY r.weekNumber ASC")
    List<AdminTeamLeadRating> findByTeamLeadAndQuarter(
        @Param("teamLeadId") UUID teamLeadId, 
        @Param("year") Integer year,
        @Param("startWeek") Integer startWeek,
        @Param("endWeek") Integer endWeek);

    // Get latest rating for a team lead
    @Query("SELECT r FROM AdminTeamLeadRating r WHERE r.teamLeadId = :teamLeadId ORDER BY r.year DESC, r.weekNumber DESC LIMIT 1")
    Optional<AdminTeamLeadRating> findLatestByTeamLeadId(@Param("teamLeadId") UUID teamLeadId);

    // Get all team lead ratings for a week
    List<AdminTeamLeadRating> findByWeekNumberAndYearOrderByCreatedAtDesc(Integer weekNumber, Integer year);

    // Get average scores for a team lead
    @Query("SELECT AVG(r.leadershipScore) FROM AdminTeamLeadRating r WHERE r.teamLeadId = :teamLeadId AND r.year = :year")
    Double getAverageLeadershipScore(@Param("teamLeadId") UUID teamLeadId, @Param("year") Integer year);

    @Query("SELECT AVG(r.teamManagementScore) FROM AdminTeamLeadRating r WHERE r.teamLeadId = :teamLeadId AND r.year = :year")
    Double getAverageTeamManagementScore(@Param("teamLeadId") UUID teamLeadId, @Param("year") Integer year);

    // Check if rating exists for week
    boolean existsByTeamLeadIdAndWeekNumberAndYear(UUID teamLeadId, Integer weekNumber, Integer year);

    // Get team leads without ratings for current week
    @Query("SELECT DISTINCT p.id FROM Profile p WHERE p.isTeamLead = true " +
           "AND p.id NOT IN (SELECT r.teamLeadId FROM AdminTeamLeadRating r WHERE r.weekNumber = :weekNumber AND r.year = :year)")
    List<UUID> findTeamLeadsWithoutRatingForWeek(@Param("weekNumber") Integer weekNumber, @Param("year") Integer year);
}
