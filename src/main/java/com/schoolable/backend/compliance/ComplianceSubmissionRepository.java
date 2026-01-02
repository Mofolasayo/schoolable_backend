package com.schoolable.backend.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceSubmissionRepository extends JpaRepository<ComplianceSubmission, UUID> {
    
    List<ComplianceSubmission> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<ComplianceSubmission> findByPolicyIdOrderByCreatedAtDesc(UUID policyId);
    
    Optional<ComplianceSubmission> findByPolicyIdAndUserId(UUID policyId, UUID userId);
    
    List<ComplianceSubmission> findByUserIdAndStatus(UUID userId, String status);
    
    @Query("SELECT s FROM ComplianceSubmission s WHERE s.policy.id = :policyId AND s.status = 'submitted'")
    List<ComplianceSubmission> findPendingReviewsByPolicy(UUID policyId);
    
    @Query("SELECT COUNT(s) FROM ComplianceSubmission s WHERE s.policy.id = :policyId AND (s.status = 'approved' OR s.status = 'submitted')")
    long countCompliantByPolicy(UUID policyId);
    
    @Query("SELECT COUNT(s) FROM ComplianceSubmission s WHERE s.policy.id = :policyId")
    long countTotalByPolicy(UUID policyId);
    
    @Query("SELECT s FROM ComplianceSubmission s WHERE s.userId = :userId AND s.status = 'pending' ORDER BY s.policy.deadline ASC")
    List<ComplianceSubmission> findPendingItemsByUser(UUID userId);

    // For sub-metric calculation
    long countByUserIdAndStatus(UUID userId, String status);
    
    @Query("SELECT COUNT(s) FROM ComplianceSubmission s WHERE s.userId = :userId")
    long countByUserId(UUID userId);
}

