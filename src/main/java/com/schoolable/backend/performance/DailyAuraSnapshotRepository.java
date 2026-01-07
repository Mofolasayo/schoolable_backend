package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyAuraSnapshotRepository extends JpaRepository<DailyAuraSnapshot, Long> {

    Optional<DailyAuraSnapshot> findByEmployeeIdAndSnapshotDate(UUID employeeId, LocalDate snapshotDate);

    List<DailyAuraSnapshot> findByEmployeeIdOrderBySnapshotDateDesc(UUID employeeId);

    List<DailyAuraSnapshot> findByEmployeeIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
        UUID employeeId, LocalDate startDate, LocalDate endDate);

    // Get latest snapshot for an employee
    @Query("SELECT d FROM DailyAuraSnapshot d WHERE d.employeeId = :employeeId ORDER BY d.snapshotDate DESC LIMIT 1")
    Optional<DailyAuraSnapshot> findLatestByEmployeeId(@Param("employeeId") UUID employeeId);

    // Get snapshot from N days ago
    @Query("SELECT d FROM DailyAuraSnapshot d WHERE d.employeeId = :employeeId AND d.snapshotDate = :date")
    Optional<DailyAuraSnapshot> findByEmployeeAndDate(@Param("employeeId") UUID employeeId, @Param("date") LocalDate date);

    // Get average Aura for last N days
    @Query("SELECT AVG(d.dailyAura) FROM DailyAuraSnapshot d WHERE d.employeeId = :employeeId AND d.snapshotDate >= :startDate")
    BigDecimal getAverageAuraSince(@Param("employeeId") UUID employeeId, @Param("startDate") LocalDate startDate);

    // Get weekly trend (7 snapshots)
    @Query("SELECT d FROM DailyAuraSnapshot d WHERE d.employeeId = :employeeId AND d.snapshotDate >= :startDate ORDER BY d.snapshotDate ASC")
    List<DailyAuraSnapshot> getWeeklyTrend(@Param("employeeId") UUID employeeId, @Param("startDate") LocalDate startDate);

    // Check if snapshot exists for date
    boolean existsByEmployeeIdAndSnapshotDate(UUID employeeId, LocalDate snapshotDate);

    // Get all employees who need daily calculation
    @Query("SELECT DISTINCT p.id FROM Profile p WHERE p.status = 'active' AND p.profileCompletedAt IS NOT NULL")
    List<UUID> findAllActiveEmployeeIds();

    // Get previous day snapshot for change calculation
    @Query("SELECT d FROM DailyAuraSnapshot d WHERE d.employeeId = :employeeId AND d.snapshotDate < :date ORDER BY d.snapshotDate DESC LIMIT 1")
    Optional<DailyAuraSnapshot> findPreviousSnapshot(@Param("employeeId") UUID employeeId, @Param("date") LocalDate date);
}
