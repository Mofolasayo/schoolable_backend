package com.schoolable.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class AiJobService {

    private final AiJobRepository aiJobRepository;
    private final ObjectMapper objectMapper;

    public AiJobService(AiJobRepository aiJobRepository, ObjectMapper objectMapper) {
        this.aiJobRepository = aiJobRepository;
        this.objectMapper = objectMapper;
    }

    public AiJob enqueueJob(String jobType, Map<String, Object> payload, int priority) {
        AiJob job = new AiJob();
        job.setJobType(jobType);
        job.setPriority(priority);
        job.setStatus(AiJob.Status.PENDING);
        job.setNextRunAt(OffsetDateTime.now());
        job.setPayload(serializePayload(payload));
        return aiJobRepository.save(job);
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI job payload", e);
        }
    }
}
