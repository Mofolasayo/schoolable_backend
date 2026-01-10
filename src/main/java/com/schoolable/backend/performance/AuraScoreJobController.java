package com.schoolable.backend.performance;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/performance/aura-jobs")
public class AuraScoreJobController {

    private final AuraScoreJobRepository jobRepository;

    public AuraScoreJobController(AuraScoreJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        AuraScoreJob job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Job not found"));
        }

        UUID requesterId = (UUID) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));

        if (!isAdmin && (job.getRequestedBy() == null || !job.getRequestedBy().equals(requesterId))) {
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
            "requestedBy", job.getRequestedBy(),
            "createdAt", job.getCreatedAt(),
            "updatedAt", job.getUpdatedAt()
        ));
    }
}
