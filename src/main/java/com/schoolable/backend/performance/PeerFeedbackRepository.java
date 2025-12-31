package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for peer feedback.
 * Used for Collaboration pillar calculation (Peer Support criterion).
 */
public interface PeerFeedbackRepository extends JpaRepository<PeerFeedback, Long> {

    // Find all feedback received by an employee
    List<PeerFeedback> findByToEmployeeIdOrderByCreatedAtDesc(UUID toEmployeeId);

    // Find feedback for a specific quarter
    List<PeerFeedback> findByToEmployeeIdAndQuarterAndYear(
        UUID toEmployeeId, String quarter, Integer year);

    // Find feedback given by an employee
    List<PeerFeedback> findByFromEmployeeIdAndQuarterAndYear(
        UUID fromEmployeeId, String quarter, Integer year);

    // Count how many feedback submissions an employee has received
    long countByToEmployeeIdAndQuarterAndYear(UUID toEmployeeId, String quarter, Integer year);

    // Calculate average support rating for an employee in a quarter
    @Query("SELECT AVG(pf.supportRating) FROM PeerFeedback pf " +
           "WHERE pf.toEmployeeId = :employeeId AND pf.quarter = :quarter AND pf.year = :year " +
           "AND pf.status = 'submitted'")
    Double getAverageSupportRating(
        @Param("employeeId") UUID employeeId,
        @Param("quarter") String quarter,
        @Param("year") Integer year);

    // Calculate overall average rating (all 3 ratings combined)
    @Query("SELECT AVG((pf.supportRating + COALESCE(pf.collaborationRating, pf.supportRating) + " +
           "COALESCE(pf.communicationRating, pf.supportRating)) / 3.0) " +
           "FROM PeerFeedback pf " +
           "WHERE pf.toEmployeeId = :employeeId AND pf.quarter = :quarter AND pf.year = :year " +
           "AND pf.status = 'submitted'")
    Double getOverallAverageRating(
        @Param("employeeId") UUID employeeId,
        @Param("quarter") String quarter,
        @Param("year") Integer year);

    // Check if employee has already given feedback to another employee this quarter
    boolean existsByFromEmployeeIdAndToEmployeeIdAndQuarterAndYear(
        UUID fromEmployeeId, UUID toEmployeeId, String quarter, Integer year);
}
