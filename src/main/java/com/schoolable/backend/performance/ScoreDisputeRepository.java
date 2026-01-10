package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoreDisputeRepository extends JpaRepository<ScoreDispute, UUID> {

    List<ScoreDispute> findByEmployeeIdOrderBySubmittedAtDesc(UUID employeeId);
    
    List<ScoreDispute> findByStatusOrderBySubmittedAtAsc(ScoreDispute.DisputeStatus status);
    
    long countByStatus(ScoreDispute.DisputeStatus status);
}
