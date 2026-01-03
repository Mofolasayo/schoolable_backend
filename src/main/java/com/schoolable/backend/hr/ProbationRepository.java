package com.schoolable.backend.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProbationRepository extends JpaRepository<ProbationRecord, UUID> {
    
    Optional<ProbationRecord> findByEmployeeId(UUID employeeId);
    
    List<ProbationRecord> findByStatus(String status);
    
    List<ProbationRecord> findByStatusIn(List<String> statuses);
    
    @Query("SELECT p FROM ProbationRecord p WHERE p.status NOT IN ('confirmed', 'terminated') ORDER BY p.currentEndDate ASC")
    List<ProbationRecord> findActiveProbations();
    
    @Query("SELECT p FROM ProbationRecord p WHERE p.currentEndDate < :date AND p.status NOT IN ('confirmed', 'terminated')")
    List<ProbationRecord> findOverdue(LocalDate date);
    
    @Query("SELECT p FROM ProbationRecord p WHERE p.currentEndDate BETWEEN :startDate AND :endDate AND p.status = 'pending'")
    List<ProbationRecord> findDueForConfirmation(LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT COUNT(p) FROM ProbationRecord p WHERE p.status NOT IN ('confirmed', 'terminated')")
    long countActiveProbations();
    
    @Query("SELECT COUNT(p) FROM ProbationRecord p WHERE p.appraisalScore < 50 AND p.status NOT IN ('confirmed', 'terminated')")
    long countAtRisk();
    
    @Query("SELECT p FROM ProbationRecord p WHERE p.supervisorId = :supervisorId AND p.status NOT IN ('confirmed', 'terminated')")
    List<ProbationRecord> findBySupervisor(UUID supervisorId);
}
