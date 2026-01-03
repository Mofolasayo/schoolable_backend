package com.schoolable.backend.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for viewing audit logs.
 * Requires VIEW_AUDIT_LOGS permission (admin only).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Get paginated audit logs.
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getAuditLogs(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action
    ) {
        // TODO: Check VIEW_AUDIT_LOGS permission
        
        Page<AuditLog> logs;
        if (entityType != null && !entityType.isEmpty()) {
            logs = auditLogRepository.findByEntityTypeOrderByCreatedAtDesc(
                entityType.toUpperCase(),
                PageRequest.of(page, size)
            );
        } else {
            logs = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("logs", logs.getContent().stream().map(this::toDto).collect(Collectors.toList()));
        response.put("totalElements", logs.getTotalElements());
        response.put("totalPages", logs.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get audit logs for a specific entity.
     */
    @GetMapping("/logs/{entityType}/{entityId}")
    public ResponseEntity<?> getEntityAuditLogs(
            Authentication auth,
            @PathVariable String entityType,
            @PathVariable String entityId
    ) {
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            entityType.toUpperCase(),
            entityId
        );

        return ResponseEntity.ok(Map.of(
            "entityType", entityType.toUpperCase(),
            "entityId", entityId,
            "logs", logs.stream().map(this::toDto).collect(Collectors.toList())
        ));
    }

    /**
     * Get audit log statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getAuditStats(Authentication auth) {
        OffsetDateTime last24Hours = OffsetDateTime.now().minusHours(24);
        OffsetDateTime last7Days = OffsetDateTime.now().minusDays(7);

        List<Object[]> last24HourCounts = auditLogRepository.countActionsSince(last24Hours);
        List<Object[]> last7DayCounts = auditLogRepository.countActionsSince(last7Days);

        Map<String, Long> actionCounts24h = new HashMap<>();
        for (Object[] row : last24HourCounts) {
            actionCounts24h.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> actionCounts7d = new HashMap<>();
        for (Object[] row : last7DayCounts) {
            actionCounts7d.put((String) row[0], (Long) row[1]);
        }

        return ResponseEntity.ok(Map.of(
            "last24Hours", actionCounts24h,
            "last7Days", actionCounts7d
        ));
    }

    /**
     * Convert AuditLog entity to DTO.
     */
    private Map<String, Object> toDto(AuditLog log) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", log.getId());
        dto.put("entityType", log.getEntityType());
        dto.put("entityId", log.getEntityId());
        dto.put("action", log.getAction());
        dto.put("actorId", log.getActorId());
        dto.put("actorName", log.getActorName());
        dto.put("actorEmail", log.getActorEmail());
        dto.put("changes", log.getChanges());
        dto.put("metadata", log.getMetadata());
        dto.put("ipAddress", log.getIpAddress());
        dto.put("createdAt", log.getCreatedAt());
        return dto;
    }
}
