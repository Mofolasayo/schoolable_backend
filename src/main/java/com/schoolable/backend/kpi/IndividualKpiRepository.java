package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Individual KPIs
 */
public interface IndividualKpiRepository extends JpaRepository<IndividualKpi, UUID> {

    /**
     * Find all KPIs for a specific employee
     */
    List<IndividualKpi> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    /**
     * Find active KPIs for an employee in a specific quarter/year
     */
    @Query("SELECT k FROM IndividualKpi k WHERE k.employeeId = :employeeId " +
           "AND k.quarter = :quarter AND k.year = :year AND k.isActive = true " +
           "ORDER BY k.weight DESC")
    List<IndividualKpi> findActiveByEmployeeAndPeriod(
        @Param("employeeId") UUID employeeId,
        @Param("quarter") String quarter,
        @Param("year") Integer year
    );

    /**
     * Find all KPIs set by a team lead
     */
    List<IndividualKpi> findBySetByIdOrderByCreatedAtDesc(UUID setById);

    /**
     * Find all KPIs set by a team lead for a specific quarter/year
     */
    @Query("SELECT k FROM IndividualKpi k WHERE k.setById = :setById " +
           "AND k.quarter = :quarter AND k.year = :year " +
           "ORDER BY k.employeeId, k.weight DESC")
    List<IndividualKpi> findBySetByIdAndPeriod(
        @Param("setById") UUID setById,
        @Param("quarter") String quarter,
        @Param("year") Integer year
    );

    /**
     * Find KPIs for a department
     */
    @Query("SELECT k FROM IndividualKpi k WHERE k.department = :department " +
           "AND k.quarter = :quarter AND k.year = :year AND k.isActive = true " +
           "ORDER BY k.employeeId, k.weight DESC")
    List<IndividualKpi> findByDepartmentAndPeriod(
        @Param("department") String department,
        @Param("quarter") String quarter,
        @Param("year") Integer year
    );

    /**
     * Calculate average achievement for an employee
     */
    @Query("SELECT AVG(k.achievementPercentage) FROM IndividualKpi k " +
           "WHERE k.employeeId = :employeeId AND k.quarter = :quarter AND k.year = :year " +
           "AND k.isActive = true")
    Double getAverageAchievement(
        @Param("employeeId") UUID employeeId,
        @Param("quarter") String quarter,
        @Param("year") Integer year
    );

    /**
     * Get total weight of KPIs for an employee (should sum to 100)
     */
    @Query("SELECT COALESCE(SUM(k.weight), 0) FROM IndividualKpi k " +
           "WHERE k.employeeId = :employeeId AND k.quarter = :quarter AND k.year = :year " +
           "AND k.isActive = true")
    Integer getTotalWeight(
        @Param("employeeId") UUID employeeId,
        @Param("quarter") String quarter,
        @Param("year") Integer year
    );

    // ============ APPROVAL WORKFLOW QUERIES ============
    
    /**
     * Find KPIs by approval status
     */
    List<IndividualKpi> findByApprovalStatus(IndividualKpi.ApprovalStatus status);

    /**
     * Find KPIs by approval status and department
     */
    List<IndividualKpi> findByApprovalStatusAndDepartment(
        IndividualKpi.ApprovalStatus status, String department);

    /**
     * Count KPIs by approval status
     */
    long countByApprovalStatus(IndividualKpi.ApprovalStatus status);

    /**
     * Find pending KPIs for a specific team lead's employees
     */
    @Query("SELECT k FROM IndividualKpi k WHERE k.setById = :setById " +
           "AND k.approvalStatus = 'PENDING_APPROVAL' ORDER BY k.submittedAt DESC")
    List<IndividualKpi> findPendingByTeamLead(@Param("setById") UUID setById);

    // ============ CASCADING QUERIES ============

    /**
     * Find child KPIs for a parent
     */
    List<IndividualKpi> findByParentKpiIdOrderByEmployeeId(UUID parentKpiId);

    /**
     * Find KPIs by cascade level
     */
    List<IndividualKpi> findByCascadeLevelOrderByCreatedAtDesc(String cascadeLevel);

    List<IndividualKpi> findByAutoProgressEnabledTrueAndQuarterAndYear(String quarter, Integer year);
}
