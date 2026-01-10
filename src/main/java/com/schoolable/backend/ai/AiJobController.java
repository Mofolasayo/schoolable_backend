package com.schoolable.backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/jobs")
public class AiJobController {

    private final AiJobRepository aiJobRepository;
    private final ObjectMapper objectMapper;
    public AiJobController(AiJobRepository aiJobRepository, ObjectMapper objectMapper) {
        this.aiJobRepository = aiJobRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        AiJob job = aiJobRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Job not found"));
        }

        UUID requesterId = (UUID) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));

        if (!isAdmin && !isOwner(job, requesterId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(Map.of(
            "id", job.getId(),
            "jobType", job.getJobType(),
            "status", job.getStatus(),
            "attempts", job.getAttempts(),
            "maxAttempts", job.getMaxAttempts(),
            "nextRunAt", job.getNextRunAt(),
            "lastError", job.getLastError(),
            "createdAt", job.getCreatedAt(),
            "updatedAt", job.getUpdatedAt()
        ));
    }

    private boolean isOwner(AiJob job, UUID requesterId) {
        try {
            Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
            Object requestedBy = payload.get("requestedBy");
            if (requestedBy != null && requesterId.toString().equals(requestedBy.toString())) {
                return true;
            }
            Object employeeId = payload.get("employeeId");
            if (employeeId != null && requesterId.toString().equals(employeeId.toString())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
