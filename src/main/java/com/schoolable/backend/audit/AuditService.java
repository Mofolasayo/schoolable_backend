package com.schoolable.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating audit log entries.
 * Call this from controllers or services to log important changes.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Action constants
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_VIEW = "VIEW";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_RATE = "RATE";
    public static final String ACTION_ASSIGN = "ASSIGN";

    // Entity type constants
    public static final String ENTITY_PROFILE = "PROFILE";
    public static final String ENTITY_TASK = "TASK";
    public static final String ENTITY_ATTENDANCE = "ATTENDANCE";
    public static final String ENTITY_ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String ENTITY_COMPLIANCE = "COMPLIANCE";
    public static final String ENTITY_TRAINING = "TRAINING";
    public static final String ENTITY_PERFORMANCE = "PERFORMANCE";
    public static final String ENTITY_KPI = "KPI";
    public static final String ENTITY_MESSAGE = "MESSAGE";
    public static final String ENTITY_AUTH = "AUTH";

    /**
     * Log an action with the current authenticated user as the actor.
     */
    public void log(String entityType, String entityId, String action) {
        log(entityType, entityId, action, null, null);
    }

    /**
     * Log an action with changes.
     */
    public void log(String entityType, String entityId, String action, Object before, Object after) {
        try {
            AuditLog log = new AuditLog();
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setAction(action);

            // Get actor from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String) {
                try {
                    UUID userId = UUID.fromString((String) auth.getPrincipal());
                    log.setActorId(userId);
                    
                    Optional<Profile> profile = profileRepository.findById(userId);
                    profile.ifPresent(p -> {
                        log.setActorName(p.getFullName());
                        log.setActorEmail(p.getEmail());
                    });
                } catch (IllegalArgumentException e) {
                    // Principal is not a UUID, might be anonymous
                }
            }

            // Record changes as JSON
            if (before != null || after != null) {
                Map<String, Object> changes = new HashMap<>();
                if (before != null) changes.put("before", before);
                if (after != null) changes.put("after", after);
                log.setChanges(objectMapper.writeValueAsString(changes));
            }

            // Capture request metadata
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    log.setIpAddress(getClientIp(request));
                    log.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception e) {
                // Request context not available (e.g., async processing)
            }

            saveSafely(log);
        } catch (JsonProcessingException e) {
            // Log error but don't fail the main operation
            log.warn("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Convenience method to log a CREATE action with a specific actor.
     */
    public void logCreate(String entityType, String entityId, UUID actorId) {
        try {
            AuditLog log = new AuditLog();
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setAction(ACTION_CREATE);
            if (actorId != null) {
                log.setActorId(actorId);
                Optional<Profile> profile = profileRepository.findById(actorId);
                profile.ifPresent(p -> {
                    log.setActorName(p.getFullName());
                    log.setActorEmail(p.getEmail());
                });
            }

            captureRequestMetadata(log);
            saveSafely(log);
        } catch (Exception e) {
            log.warn("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Convenience method to log an UPDATE action with changes and a specific actor.
     */
    public void logUpdate(String entityType, String entityId, Map<String, ?> changes, UUID actorId) {
        try {
            AuditLog log = new AuditLog();
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setAction(ACTION_UPDATE);
            if (actorId != null) {
                log.setActorId(actorId);
                Optional<Profile> profile = profileRepository.findById(actorId);
                profile.ifPresent(p -> {
                    log.setActorName(p.getFullName());
                    log.setActorEmail(p.getEmail());
                });
            }

            if (changes != null && !changes.isEmpty()) {
                log.setChanges(objectMapper.writeValueAsString(changes));
            }

            captureRequestMetadata(log);
            saveSafely(log);
        } catch (Exception e) {
            log.warn("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Helper to capture request metadata.
     */
    private void captureRequestMetadata(AuditLog log) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                log.setIpAddress(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // Request context not available (e.g., async processing)
        }
    }

    /**
     * Log an action for a specific actor (used when actor is known but not in security context).
     */
    public void logForActor(String entityType, String entityId, String action, UUID actorId, String actorName) {
        try {
            AuditLog log = new AuditLog(entityType, entityId, action, actorId, actorName);

            // Capture request metadata
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    log.setIpAddress(getClientIp(request));
                    log.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception e) {
                // Request context not available
            }

            saveSafely(log);
        } catch (Exception e) {
            log.warn("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Log with additional metadata.
     */
    public void logWithMetadata(String entityType, String entityId, String action, Map<String, Object> metadata) {
        try {
            AuditLog log = new AuditLog();
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setAction(action);

            // Get actor from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String) {
                try {
                    UUID userId = UUID.fromString((String) auth.getPrincipal());
                    log.setActorId(userId);
                    
                    Optional<Profile> profile = profileRepository.findById(userId);
                    profile.ifPresent(p -> {
                        log.setActorName(p.getFullName());
                        log.setActorEmail(p.getEmail());
                    });
                } catch (IllegalArgumentException e) {
                    // Principal is not a UUID
                }
            }

            if (metadata != null && !metadata.isEmpty()) {
                log.setMetadata(objectMapper.writeValueAsString(metadata));
            }

            // Capture request metadata
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    log.setIpAddress(getClientIp(request));
                    log.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception e) {
                // Request context not available
            }

            saveSafely(log);
        } catch (JsonProcessingException e) {
            log.warn("Failed to create audit log: {}", e.getMessage());
        }
    }

    private void saveSafely(AuditLog logEntry) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> auditLogRepository.save(logEntry));
        } catch (Exception e) {
            log.warn("Failed to persist audit log: {}", e.getMessage());
        }
    }

    /**
     * Extract client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Get first IP if multiple
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
