package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for training records/certificates.
 * Used for Growth & Learning pillar calculation.
 */
public interface TrainingRecordRepository extends JpaRepository<TrainingRecord, Long> {

    // Find all trainings for an employee
    List<TrainingRecord> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    // Find completed trainings for an employee
    List<TrainingRecord> findByEmployeeIdAndStatus(UUID employeeId, String status);

    // Count completed trainings after a date (for quarter calculation)
    long countByEmployeeIdAndStatusAndCompletionDateAfter(
        UUID employeeId, String status, LocalDate afterDate);

    // Count total completed trainings for an employee
    long countByEmployeeIdAndStatus(UUID employeeId, String status);

    // Find trainings by skill category
    List<TrainingRecord> findByEmployeeIdAndSkillCategory(UUID employeeId, String skillCategory);

    // Get training summary for a quarter (only approved count)
    @Query("SELECT COUNT(t) FROM TrainingRecord t WHERE t.employeeId = :employeeId " +
           "AND t.status = 'approved' AND t.completionDate >= :startDate")
    long countCompletedTrainingsInPeriod(
        @Param("employeeId") UUID employeeId, 
        @Param("startDate") LocalDate startDate);

    // Count approved certificates for a specific quarter
    @Query("SELECT COUNT(t) FROM TrainingRecord t WHERE t.employeeId = :employeeId " +
           "AND t.status = 'approved' AND t.quarter = :quarter AND t.year = :year")
    long countApprovedInQuarter(
        @Param("employeeId") UUID employeeId,
        @Param("quarter") String quarter,
        @Param("year") Integer year);

    // Find all pending certificates (for admin review)
    List<TrainingRecord> findByStatusOrderByCreatedAtAsc(String status);

    // Find certificate by employee for a specific quarter
    Optional<TrainingRecord> findByEmployeeIdAndQuarterAndYear(
        UUID employeeId, String quarter, Integer year);

    // Find all certificates for a quarter (admin view)
    List<TrainingRecord> findByQuarterAndYearOrderByCreatedAtDesc(String quarter, Integer year);

    // Check if employee has approved certificate for quarter
    boolean existsByEmployeeIdAndQuarterAndYearAndStatus(
        UUID employeeId, String quarter, Integer year, String status);

    // HR Management queries - for admin certificate review
    List<TrainingRecord> findAllByOrderByCreatedAtDesc();
    
    long countByStatus(String status);
    
    List<TrainingRecord> findByStatus(String status);
}


