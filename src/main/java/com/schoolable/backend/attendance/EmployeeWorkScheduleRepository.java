package com.schoolable.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeWorkScheduleRepository extends JpaRepository<EmployeeWorkSchedule, UUID> {
    @Query("SELECT e FROM EmployeeWorkSchedule e WHERE e.employeeId = :employeeId AND e.effectiveStartDate <= :date AND (e.effectiveEndDate IS NULL OR e.effectiveEndDate >= :date) ORDER BY e.effectiveStartDate DESC")
    Optional<EmployeeWorkSchedule> findActiveSchedule(@Param("employeeId") UUID employeeId, @Param("date") LocalDate date);

    List<EmployeeWorkSchedule> findByEmployeeIdOrderByEffectiveStartDateDesc(UUID employeeId);
}
