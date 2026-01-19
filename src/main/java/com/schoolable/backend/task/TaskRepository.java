package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    @Query("""
        SELECT DISTINCT t FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        ORDER BY t.createdAt DESC
    """)
    List<Task> findByAssigneeIdOrderByCreatedAtDesc(@Param("assigneeId") UUID assigneeId);

    @Query("""
        SELECT DISTINCT t FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.createdAt >= :after
        ORDER BY t.createdAt DESC
    """)
    List<Task> findByAssigneeIdAndCreatedAtAfterOrderByCreatedAtDesc(
        @Param("assigneeId") UUID assigneeId,
        @Param("after") OffsetDateTime after
    );
    List<Task> findAllByOrderByCreatedAtDesc();
    
    // Count methods for performance calculations
    @Query("""
        SELECT COUNT(DISTINCT t.id) FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.createdAt >= :after
    """)
    long countByAssigneeIdAndCreatedAtAfter(
        @Param("assigneeId") UUID assigneeId,
        @Param("after") OffsetDateTime after
    );

    @Query("""
        SELECT COUNT(DISTINCT t.id) FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.status = :status
        AND t.createdAt >= :after
    """)
    long countByAssigneeIdAndStatusAndCreatedAtAfter(
        @Param("assigneeId") UUID assigneeId,
        @Param("status") String status,
        @Param("after") OffsetDateTime after
    );
    
    List<Task> findByOrganizationOrderByCreatedAtDesc(String organization);
    Optional<Task> findTopByRecurringTemplateIdOrderByCreatedAtDesc(UUID recurringTemplateId);

    // Quality Rating Methods
    
    // Find tasks pending rating for a specific creator
    List<Task> findByCreatedByAndRatingPendingTrue(UUID createdBy);
    
    // Get average quality rating for an assignee
    @Query("""
        SELECT AVG(t.qualityRating) FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.qualityRating IS NOT NULL
    """)
    Double getAverageQualityRating(@Param("assigneeId") UUID assigneeId);
    
    // Get average quality rating for an assignee in a period
    @Query("""
        SELECT AVG(t.qualityRating) FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.qualityRating IS NOT NULL
        AND t.createdAt >= :after
    """)
    Double getAverageQualityRatingAfter(@Param("assigneeId") UUID assigneeId, @Param("after") OffsetDateTime after);
    
    // Count rated tasks for an assignee
    @Query("""
        SELECT COUNT(DISTINCT t.id) FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.qualityRating IS NOT NULL
        AND t.createdAt >= :after
    """)
    long countRatedTasksAfter(@Param("assigneeId") UUID assigneeId, @Param("after") OffsetDateTime after);
    
    // Response time calculation - get tasks with updates
    @Query("""
        SELECT DISTINCT t FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.status = 'Completed'
        AND t.createdAt >= :after
    """)
    List<Task> findCompletedTasksAfter(@Param("assigneeId") UUID assigneeId, @Param("after") OffsetDateTime after);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.organization = :department AND t.status IN :statuses AND COALESCE(t.completedAt, t.updatedAt) BETWEEN :start AND :end")
    long countByDepartmentStatusAndUpdatedAtBetween(@Param("department") String department, @Param("statuses") List<String> statuses, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("""
        SELECT COUNT(DISTINCT t.id) FROM Task t
        LEFT JOIN TaskAssignee ta ON ta.taskId = t.id AND ta.isActive = true
        WHERE (ta.userId = :assigneeId OR t.assigneeId = :assigneeId)
        AND t.status IN :statuses
        AND COALESCE(t.completedAt, t.updatedAt) BETWEEN :start AND :end
    """)
    long countByAssigneeStatusAndUpdatedAtBetween(@Param("assigneeId") UUID assigneeId, @Param("statuses") List<String> statuses, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
