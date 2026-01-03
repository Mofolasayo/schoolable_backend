package com.schoolable.backend.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PipRepository extends JpaRepository<PipRecord, Long> {
    
    Optional<PipRecord> findByEmployeeIdAndStatus(UUID employeeId, String status);
    
    List<PipRecord> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    
    List<PipRecord> findByStatus(String status);
    
    @Query("SELECT p FROM PipRecord p WHERE p.status = 'active' ORDER BY p.endDate ASC")
    List<PipRecord> findActivePips();
    
    @Query("SELECT COUNT(p) FROM PipRecord p WHERE p.status = 'active'")
    long countActivePips();
    
    @Query("SELECT p FROM PipRecord p WHERE p.supervisorId = :supervisorId AND p.status = 'active'")
    List<PipRecord> findActiveBySupervisor(UUID supervisorId);
    
    @Query("SELECT p FROM PipRecord p WHERE p.endDate < CURRENT_DATE AND p.status = 'active'")
    List<PipRecord> findOverduePips();
}
