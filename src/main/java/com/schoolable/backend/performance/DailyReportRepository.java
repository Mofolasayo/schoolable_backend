package com.schoolable.backend.performance;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Daily Reports
 */
public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    /**
     * Find report for a specific employee on a specific date
     */
    Optional<DailyReport> findByEmployeeIdAndReportDate(UUID employeeId, LocalDate reportDate);

    /**
     * Find all reports for an employee ordered by date desc
     */
    List<DailyReport> findByEmployeeIdOrderByReportDateDesc(UUID employeeId);

    /**
     * Find reports for an employee within a date range
     */
    @Query("SELECT r FROM DailyReport r WHERE r.employeeId = :employeeId " +
           "AND r.reportDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.reportDate DESC")
    List<DailyReport> findByEmployeeAndDateRange(
        @Param("employeeId") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find recent reports for an employee (with limit)
     */
    List<DailyReport> findByEmployeeIdOrderByReportDateDesc(UUID employeeId, Pageable pageable);

    /**
     * Find reports for a department on a specific date
     */
    @Query("SELECT r FROM DailyReport r, com.schoolable.backend.profile.Profile p " +
           "WHERE r.employeeId = p.id AND p.department = :department " +
           "AND r.reportDate = :date ORDER BY r.createdAt DESC")
    List<DailyReport> findByDepartmentAndDate(
        @Param("department") String department,
        @Param("date") LocalDate date
    );

    /**
     * Find reports pending AI grading
     */
    @Query("SELECT r FROM DailyReport r WHERE r.aiScore IS NULL ORDER BY r.createdAt ASC")
    List<DailyReport> findPendingAiGrading();

    /**
     * Get average AI score for an employee in a date range
     */
    @Query("SELECT AVG(r.aiScore) FROM DailyReport r WHERE r.employeeId = :employeeId " +
           "AND r.reportDate BETWEEN :startDate AND :endDate AND r.aiScore IS NOT NULL")
    Double getAverageAiScore(
        @Param("employeeId") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(r) FROM DailyReport r, com.schoolable.backend.profile.Profile p " +
           "WHERE r.employeeId = p.id AND p.department = :department " +
           "AND r.reportDate BETWEEN :startDate AND :endDate")
    Long countByDepartmentAndDateRange(
        @Param("department") String department,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT AVG(r.aiScore) FROM DailyReport r, com.schoolable.backend.profile.Profile p " +
           "WHERE r.employeeId = p.id AND p.department = :department " +
           "AND r.reportDate BETWEEN :startDate AND :endDate AND r.aiScore IS NOT NULL")
    Double getAverageAiScoreForDepartment(
        @Param("department") String department,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Count reports submitted by employee in a week
     */
    @Query("SELECT COUNT(r) FROM DailyReport r WHERE r.employeeId = :employeeId " +
           "AND r.reportDate BETWEEN :startDate AND :endDate")
    Long countByEmployeeAndWeek(
        @Param("employeeId") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Check if report exists for today
     */
    boolean existsByEmployeeIdAndReportDate(UUID employeeId, LocalDate reportDate);

    /**
     * Find all reports for team members (by department)
     */
    @Query("SELECT r FROM DailyReport r, com.schoolable.backend.profile.Profile p " +
           "WHERE r.employeeId = p.id AND p.department = :department " +
           "AND r.reportDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.reportDate DESC, p.fullName ASC")
    List<DailyReport> findByDepartmentAndDateRange(
        @Param("department") String department,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get report submission streak for an employee
     */
    @Query(value = "SELECT COUNT(*) FROM (" +
           "  SELECT report_date, report_date - (ROW_NUMBER() OVER (ORDER BY report_date DESC))::int AS grp " +
           "  FROM daily_reports WHERE employee_id = :employeeId" +
           ") sub WHERE grp = (SELECT report_date - 1 FROM daily_reports WHERE employee_id = :employeeId ORDER BY report_date DESC LIMIT 1)",
           nativeQuery = true)
    Integer getCurrentStreak(@Param("employeeId") UUID employeeId);
}
