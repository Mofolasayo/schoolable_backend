package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    // Find reviews for a specific employee
    List<PerformanceReview> findByEmployeeIdOrderByReviewYearDescQuarterDesc(UUID employeeId);

    // Find review for specific employee, quarter, and year
    Optional<PerformanceReview> findByEmployeeIdAndQuarterAndReviewYear(UUID employeeId, String quarter, Integer reviewYear);

    // Find all reviews for a specific quarter and year
    List<PerformanceReview> findByQuarterAndReviewYearOrderByCreatedAtDesc(String quarter, Integer reviewYear);

    // Find reviews by reviewer (team lead)
    List<PerformanceReview> findByReviewerIdAndQuarterAndReviewYear(UUID reviewerId, String quarter, Integer reviewYear);

    // Find reviews by status
    List<PerformanceReview> findByStatusOrderByCreatedAtDesc(String status);

    // Find submitted reviews awaiting approval
    List<PerformanceReview> findByStatusAndQuarterAndReviewYearOrderBySubmittedAtDesc(String status, String quarter, Integer reviewYear);

    // Count reviews by status for a specific quarter
    @Query("SELECT COUNT(r) FROM PerformanceReview r WHERE r.quarter = :quarter AND r.reviewYear = :year AND r.status = :status")
    long countByQuarterAndYearAndStatus(@Param("quarter") String quarter, @Param("year") Integer year, @Param("status") String status);

    // Get average scores by department for a quarter
    @Query(value = """
        SELECT 
            p.department,
            AVG(pr.quarterly_score) as avg_score,
            AVG(pr.quarterly_gpa) as avg_gpa,
            COUNT(*) as review_count
        FROM performance_reviews pr
        JOIN profiles p ON pr.employee_id = p.id
        WHERE pr.quarter = :quarter AND pr.review_year = :year AND pr.status = 'approved'
        GROUP BY p.department
        ORDER BY avg_gpa DESC
        """, nativeQuery = true)
    List<Object[]> getAverageScoresByDepartment(@Param("quarter") String quarter, @Param("year") Integer year);

    // Get team members' reviews for a team lead
    @Query(value = """
        SELECT pr.* FROM performance_reviews pr
        JOIN profiles p ON pr.employee_id = p.id
        WHERE p.department = (
            SELECT department FROM profiles WHERE id = :teamLeadId
        )
        AND pr.quarter = :quarter 
        AND pr.review_year = :year
        ORDER BY pr.quarterly_gpa DESC NULLS LAST
        """, nativeQuery = true)
    List<PerformanceReview> findTeamReviews(@Param("teamLeadId") UUID teamLeadId, @Param("quarter") String quarter, @Param("year") Integer year);

    // Find top performers
    @Query("SELECT r FROM PerformanceReview r WHERE r.quarter = :quarter AND r.reviewYear = :year AND r.status = 'approved' ORDER BY r.quarterlyGpa DESC NULLS LAST")
    List<PerformanceReview> findTopPerformers(@Param("quarter") String quarter, @Param("year") Integer year);
}
