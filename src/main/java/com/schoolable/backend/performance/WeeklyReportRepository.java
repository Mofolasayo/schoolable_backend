package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyReportRepository extends JpaRepository<WeeklyPerformanceReport, Long> {

    // Find by employee, week, and year
    Optional<WeeklyPerformanceReport> findByEmployeeIdAndWeekNumberAndYear(UUID employeeId, Integer weekNumber, Integer year);

    // Find all reports for a specific week
    List<WeeklyPerformanceReport> findByWeekNumberAndYearOrderByCreatedAtDesc(Integer weekNumber, Integer year);

    // Find reports submitted by a team lead for a specific week
    List<WeeklyPerformanceReport> findByReviewerIdAndWeekNumberAndYear(UUID reviewerId, Integer weekNumber, Integer year);

    // Find all reports for an employee (trend view)
    List<WeeklyPerformanceReport> findByEmployeeIdAndYearOrderByWeekNumberDesc(UUID employeeId, Integer year);

    // Find all reports for an employee across all time
    List<WeeklyPerformanceReport> findByEmployeeIdOrderByYearDescWeekNumberDesc(UUID employeeId);

    // Find reports in a week range (for quarterly aggregation)
    @Query("SELECT w FROM WeeklyPerformanceReport w WHERE w.weekNumber >= :startWeek AND w.weekNumber <= :endWeek AND w.year = :year AND w.status = 'submitted'")
    List<WeeklyPerformanceReport> findByWeekRangeAndYear(@Param("startWeek") Integer startWeek, 
                                                          @Param("endWeek") Integer endWeek, 
                                                          @Param("year") Integer year);

    // Count reports for a week
    long countByWeekNumberAndYear(Integer weekNumber, Integer year);

    // Get average scores for a department in a week
    @Query(value = """
        SELECT 
            p.department,
            COUNT(*) as employee_count,
            AVG(w.weekly_aura) as avg_aura,
            AVG(w.technical_score * 20) as avg_technical,
            AVG(w.behavioral_score * 20) as avg_behavioral,
            AVG(w.culture_fit_score * 20) as avg_culture,
            AVG(w.growth_learning_score * 20) as avg_growth
        FROM weekly_performance_reports w
        JOIN profiles p ON w.employee_id = p.id
        WHERE w.week_number = :weekNumber AND w.year = :year AND w.status = 'submitted'
        GROUP BY p.department
        ORDER BY avg_aura DESC
        """, nativeQuery = true)
    List<Object[]> getDepartmentWeeklySummary(@Param("weekNumber") Integer weekNumber, @Param("year") Integer year);

    // Get quarterly data for an employee
    @Query(value = """
        SELECT 
            w.employee_id,
            CASE 
                WHEN w.week_number BETWEEN 1 AND 13 THEN 'Q1'
                WHEN w.week_number BETWEEN 14 AND 26 THEN 'Q2'
                WHEN w.week_number BETWEEN 27 AND 39 THEN 'Q3'
                ELSE 'Q4'
            END as quarter,
            COUNT(*) as weeks_reported,
            AVG(w.weekly_aura) as avg_aura,
            AVG(w.technical_score * 20) as avg_technical,
            AVG(w.behavioral_score * 20) as avg_behavioral,
            AVG(w.culture_fit_score * 20) as avg_culture,
            AVG(w.growth_learning_score * 20) as avg_growth
        FROM weekly_performance_reports w
        WHERE w.employee_id = :employeeId AND w.year = :year AND w.status = 'submitted'
        GROUP BY w.employee_id, 
            CASE 
                WHEN w.week_number BETWEEN 1 AND 13 THEN 'Q1'
                WHEN w.week_number BETWEEN 14 AND 26 THEN 'Q2'
                WHEN w.week_number BETWEEN 27 AND 39 THEN 'Q3'
                ELSE 'Q4'
            END
        ORDER BY quarter
        """, nativeQuery = true)
    List<Object[]> getQuarterlyAggregation(@Param("employeeId") UUID employeeId, @Param("year") Integer year);
}
