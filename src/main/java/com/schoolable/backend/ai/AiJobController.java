package com.schoolable.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
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
    public AiJobController(AiJobRepository aiJobRepository) {
        this.aiJobRepository = aiJobRepository;
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
        JsonNode payload = job.getPayload();
        if (payload == null || payload.isNull()) {
            return false;
        }

        String requestedBy = payload.path("requestedBy").asText(null);
        if (requestedBy != null && requesterId.toString().equals(requestedBy)) {
            return true;
        }
        String employeeId = payload.path("employeeId").asText(null);
        return employeeId != null && requesterId.toString().equals(employeeId);
        return false;
    }
}
