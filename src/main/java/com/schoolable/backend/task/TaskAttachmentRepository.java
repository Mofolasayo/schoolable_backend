package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskIdOrderByIdAsc(Long taskId);
    void deleteByTaskId(Long taskId);
}
