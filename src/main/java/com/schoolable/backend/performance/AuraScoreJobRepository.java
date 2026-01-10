package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuraScoreJobRepository extends JpaRepository<AuraScoreJob, UUID> {

    @Query("SELECT j FROM AuraScoreJob j WHERE j.status = :status AND (j.nextRunAt IS NULL OR j.nextRunAt <= :now) ORDER BY j.createdAt ASC")
    List<AuraScoreJob> findDueJobs(@Param("status") AuraScoreJob.Status status, @Param("now") OffsetDateTime now);
}
