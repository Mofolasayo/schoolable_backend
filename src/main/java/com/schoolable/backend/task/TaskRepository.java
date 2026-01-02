package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByAssigneeIdOrderByCreatedAtDesc(UUID assigneeId);
    List<Task> findAllByOrderByCreatedAtDesc();
    
    // Count methods for performance calculations
    long countByAssigneeIdAndCreatedAtAfter(UUID assigneeId, OffsetDateTime after);
    long countByAssigneeIdAndStatusAndCreatedAtAfter(UUID assigneeId, String status, OffsetDateTime after);
    
    List<Task> findByOrganizationOrderByCreatedAtDesc(String organization);

    // Quality Rating Methods
    
    // Find tasks pending rating for a specific creator
    List<Task> findByCreatedByAndRatingPendingTrue(UUID createdBy);
    
    // Get average quality rating for an assignee
    @Query("SELECT AVG(t.qualityRating) FROM Task t WHERE t.assigneeId = :assigneeId AND t.qualityRating IS NOT NULL")
    Double getAverageQualityRating(@Param("assigneeId") UUID assigneeId);
    
    // Get average quality rating for an assignee in a period
    @Query("SELECT AVG(t.qualityRating) FROM Task t WHERE t.assigneeId = :assigneeId AND t.qualityRating IS NOT NULL AND t.createdAt >= :after")
    Double getAverageQualityRatingAfter(@Param("assigneeId") UUID assigneeId, @Param("after") OffsetDateTime after);
    
    // Count rated tasks for an assignee
    @Query("SELECT COUNT(t) FROM Task t WHERE t.assigneeId = :assigneeId AND t.qualityRating IS NOT NULL AND t.createdAt >= :after")
    long countRatedTasksAfter(@Param("assigneeId") UUID assigneeId, @Param("after") OffsetDateTime after);
    
    // Response time calculation - get tasks with updates
    @Query("SELECT t FROM Task t WHERE t.assigneeId = :assigneeId AND t.status = 'Completed' AND t.createdAt >= :after")
    List<Task> findCompletedTasksAfter(@Param("assigneeId") UUID assigneeId, @Param("after") OffsetDateTime after);
}
