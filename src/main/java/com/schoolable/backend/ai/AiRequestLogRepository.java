package com.schoolable.backend.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {
}
