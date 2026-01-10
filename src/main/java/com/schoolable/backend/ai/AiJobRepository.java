package com.schoolable.backend.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
    @Query("SELECT j FROM AiJob j WHERE j.status = :status AND j.nextRunAt <= :now ORDER BY j.priority DESC, j.createdAt ASC")
    List<AiJob> findDueJobs(@Param("status") AiJob.Status status, @Param("now") OffsetDateTime now);
}
