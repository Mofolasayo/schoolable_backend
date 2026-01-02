package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubMetricScoreRepository extends JpaRepository<SubMetricScore, UUID> {

    /**
     * Find all sub-metric scores for an employee in a specific quarter
     */
    List<SubMetricScore> findByEmployeeIdAndQuarterAndYear(UUID employeeId, String quarter, Integer year);

    /**
     * Find all sub-metric scores for an employee in a specific pillar and quarter
     */
    List<SubMetricScore> findByEmployeeIdAndPillarAndQuarterAndYear(
            UUID employeeId, String pillar, String quarter, Integer year);

    /**
     * Find a specific sub-metric score
     */
    Optional<SubMetricScore> findByEmployeeIdAndPillarAndSubMetricAndQuarterAndYear(
            UUID employeeId, String pillar, String subMetric, String quarter, Integer year);

    /**
     * Find all sub-metric scores for a pillar across all employees (for reporting)
     */
    List<SubMetricScore> findByPillarAndQuarterAndYear(String pillar, String quarter, Integer year);

    /**
     * Get average score for a specific sub-metric across all employees
     */
    @Query("SELECT AVG(s.score) FROM SubMetricScore s WHERE s.subMetric = :subMetric AND s.quarter = :quarter AND s.year = :year")
    Double getAverageScoreBySubMetric(String subMetric, String quarter, Integer year);

    /**
     * Get all scores for an employee ordered by pillar
     */
    List<SubMetricScore> findByEmployeeIdAndQuarterAndYearOrderByPillarAscSubMetricAsc(
            UUID employeeId, String quarter, Integer year);

    /**
     * Delete all scores for an employee in a quarter (for recalculation)
     */
    void deleteByEmployeeIdAndQuarterAndYear(UUID employeeId, String quarter, Integer year);

    /**
     * Count scores by source type for an employee
     */
    @Query("SELECT s.source, COUNT(s) FROM SubMetricScore s WHERE s.employeeId = :employeeId AND s.quarter = :quarter AND s.year = :year GROUP BY s.source")
    List<Object[]> countBySourceForEmployee(UUID employeeId, String quarter, Integer year);

    /**
     * Find weekly scores for trend analysis
     */
    List<SubMetricScore> findByEmployeeIdAndQuarterAndYearAndWeekNumberOrderByPillarAsc(
            UUID employeeId, String quarter, Integer year, Integer weekNumber);
}
