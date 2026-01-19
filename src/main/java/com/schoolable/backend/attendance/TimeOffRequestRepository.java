package com.schoolable.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimeOffRequestRepository extends JpaRepository<TimeOffRequest, UUID> {
    List<TimeOffRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<TimeOffRequest> findByStatusOrderByCreatedAtDesc(TimeOffRequest.Status status);

    List<TimeOffRequest> findAllByOrderByCreatedAtDesc();

    @Query("SELECT t FROM TimeOffRequest t WHERE t.employeeId = :employeeId AND t.status = 'APPROVED' AND t.startDate <= :date AND t.endDate >= :date")
    List<TimeOffRequest> findApprovedForDate(@Param("employeeId") UUID employeeId, @Param("date") LocalDate date);

    @Query("SELECT t FROM TimeOffRequest t WHERE t.status = 'APPROVED' AND t.startDate <= :endDate AND t.endDate >= :startDate")
    List<TimeOffRequest> findApprovedOverlappingRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM TimeOffRequest t WHERE t.employeeId = :employeeId AND t.status <> 'REJECTED' AND t.startDate <= :endDate AND t.endDate >= :startDate")
    List<TimeOffRequest> findActiveOverlappingForEmployee(
        @Param("employeeId") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
