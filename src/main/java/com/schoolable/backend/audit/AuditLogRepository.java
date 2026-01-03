package com.schoolable.backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Find logs by entity
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    // Find logs by actor
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    // Find logs by action type
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    // Find logs by entity type with pagination
    Page<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    // Find all logs with pagination
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Find logs within date range
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );

    // Find recent logs for an entity
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC LIMIT :limit")
    List<AuditLog> findRecentByEntity(
        @Param("entityType") String entityType,
        @Param("entityId") String entityId,
        @Param("limit") int limit
    );

    // Count actions by type
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since GROUP BY a.action")
    List<Object[]> countActionsSince(@Param("since") OffsetDateTime since);
}
