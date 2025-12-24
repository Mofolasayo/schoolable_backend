package com.schoolable.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    // Find attendance by user and date
    Optional<Attendance> findByUserIdAndDate(UUID userId, LocalDate date);
    
    // Find all attendance records for a date
    List<Attendance> findByDateOrderByCheckInDesc(LocalDate date);
    
    // Find all attendance records for a user
    List<Attendance> findByUserIdOrderByDateDesc(UUID userId);
    
    // Find recent attendance records (for dashboard)
    @Query("SELECT a FROM Attendance a ORDER BY a.checkIn DESC LIMIT :limit")
    List<Attendance> findRecentAttendance(@Param("limit") int limit);
    
    // Find attendance by date range
    @Query("SELECT a FROM Attendance a WHERE a.date BETWEEN :startDate AND :endDate ORDER BY a.checkIn DESC")
    List<Attendance> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Count by status for a date
    long countByDateAndStatus(LocalDate date, String status);
    
    // Find attendance that hasn't checked out yet
    List<Attendance> findByUserIdAndCheckOutIsNull(UUID userId);
}
