package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuraTrendAlertRepository extends JpaRepository<AuraTrendAlert, Long> {

    // Get unread alerts for an employee
    List<AuraTrendAlert> findByEmployeeIdAndIsReadFalseOrderByCreatedAtDesc(UUID employeeId);

    // Get all alerts for an employee
    List<AuraTrendAlert> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    // Get alerts by type
    List<AuraTrendAlert> findByAlertTypeOrderByCreatedAtDesc(String alertType);

    // Get unacknowledged alerts (for team leads/admins to review)
    List<AuraTrendAlert> findByIsAcknowledgedFalseOrderByCreatedAtDesc();

    // Get alerts for a specific week
    @Query("SELECT a FROM AuraTrendAlert a WHERE FUNCTION('WEEK', a.createdAt) = :weekNumber AND FUNCTION('YEAR', a.createdAt) = :year")
    List<AuraTrendAlert> findByWeek(@Param("weekNumber") Integer weekNumber, @Param("year") Integer year);

    // Count unread alerts for an employee
    long countByEmployeeIdAndIsReadFalse(UUID employeeId);

    // Get latest alert for an employee
    @Query("SELECT a FROM AuraTrendAlert a WHERE a.employeeId = :employeeId ORDER BY a.createdAt DESC LIMIT 1")
    AuraTrendAlert findLatestByEmployeeId(@Param("employeeId") UUID employeeId);

    // Get significant drops (> 10%) in last week
    @Query(value = "SELECT * FROM aura_trend_alerts a WHERE a.alert_type = 'SCORE_DROP' " +
           "AND a.change_percentage <= -10 AND a.created_at >= CURRENT_DATE - INTERVAL '7 days' ORDER BY a.change_percentage ASC", 
           nativeQuery = true)
    List<AuraTrendAlert> findSignificantDropsLastWeek();

    // Get consistent trends (3+ weeks)
    @Query("SELECT a FROM AuraTrendAlert a WHERE a.weeksTrending >= 3 AND a.isAcknowledged = false ORDER BY a.weeksTrending DESC")
    List<AuraTrendAlert> findConsistentTrends();
}
