package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KpiChangeRequestRepository extends JpaRepository<KpiChangeRequest, UUID> {
    List<KpiChangeRequest> findByStatusOrderByRequestedAtDesc(KpiChangeRequest.Status status);
    List<KpiChangeRequest> findByRequestedByOrderByRequestedAtDesc(UUID requestedBy);
}
