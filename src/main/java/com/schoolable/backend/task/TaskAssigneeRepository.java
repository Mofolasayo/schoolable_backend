package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, UUID> {

    /**
     * Find all assignees for a task
     */
    List<TaskAssignee> findByTaskIdAndIsActiveTrue(Long taskId);

    /**
     * Find all tasks assigned to a user
     */
    List<TaskAssignee> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Find assignees by role
     */
    List<TaskAssignee> findByTaskIdAndRole(Long taskId, String role);

    /**
     * Check if user is assigned to task
     */
    boolean existsByTaskIdAndUserIdAndIsActiveTrue(Long taskId, UUID userId);
}
