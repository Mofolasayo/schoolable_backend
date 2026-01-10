package com.schoolable.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {
    List<WorkSchedule> findByIsActiveTrue();
}
