package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for training records.
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

    // Get training summary for a quarter
    @Query("SELECT COUNT(t) FROM TrainingRecord t WHERE t.employeeId = :employeeId " +
           "AND t.status = 'completed' AND t.completionDate >= :startDate")
    long countCompletedTrainingsInPeriod(
        @Param("employeeId") UUID employeeId, 
        @Param("startDate") LocalDate startDate);
}
