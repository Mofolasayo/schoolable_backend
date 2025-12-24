package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskSubtaskRepository extends JpaRepository<TaskSubtask, Long> {
    List<TaskSubtask> findByTaskIdOrderByIdAsc(Long taskId);
    void deleteByTaskId(Long taskId);
}
