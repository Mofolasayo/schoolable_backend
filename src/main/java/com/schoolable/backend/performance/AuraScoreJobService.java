package com.schoolable.backend.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuraScoreJobService {

    private final AuraScoreJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public AuraScoreJobService(AuraScoreJobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public AuraScoreJob enqueueJob(String jobType, Map<String, Object> payload, int maxAttempts, UUID requestedBy) {
        AuraScoreJob job = new AuraScoreJob();
        job.setJobType(jobType);
        job.setMaxAttempts(maxAttempts);
        job.setRequestedBy(requestedBy);
        job.setStatus(AuraScoreJob.Status.PENDING);
        job.setNextRunAt(OffsetDateTime.now());
        try {
            job.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            job.setPayload("{}");
            job.setLastError("Failed to serialize payload: " + e.getMessage());
        }

        return jobRepository.save(job);
    }
}
