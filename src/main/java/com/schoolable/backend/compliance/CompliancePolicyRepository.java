package com.schoolable.backend.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CompliancePolicyRepository extends JpaRepository<CompliancePolicy, UUID> {
    
    List<CompliancePolicy> findByIsActiveTrueOrderByCreatedAtDesc();
    
    List<CompliancePolicy> findByDepartmentAndIsActiveTrueOrderByCreatedAtDesc(String department);
    
    List<CompliancePolicy> findByCategoryAndIsActiveTrueOrderByCreatedAtDesc(String category);
    
    @Query("SELECT p FROM CompliancePolicy p WHERE p.isActive = true AND (p.department IS NULL OR p.department = :department) ORDER BY p.createdAt DESC")
    List<CompliancePolicy> findActivePoliciesForDepartment(String department);
    
    @Query("SELECT p FROM CompliancePolicy p WHERE p.isActive = true AND p.department IS NULL ORDER BY p.createdAt DESC")
    List<CompliancePolicy> findGlobalActivePolicies();
}
