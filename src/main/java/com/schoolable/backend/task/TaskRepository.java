package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
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
}

