package com.schoolable.backend.compliance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ComplianceService {
    
    private final CompliancePolicyRepository policyRepository;
    private final ComplianceSubmissionRepository submissionRepository;
    private final ProfileRepository profileRepository;
    
    public ComplianceService(
            CompliancePolicyRepository policyRepository,
            ComplianceSubmissionRepository submissionRepository,
            ProfileRepository profileRepository) {
        this.policyRepository = policyRepository;
        this.submissionRepository = submissionRepository;
        this.profileRepository = profileRepository;
    }
    
    // ==================== POLICY MANAGEMENT (Admin) ====================
    
    public List<CompliancePolicy> getAllPolicies() {
        return policyRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }
    
    public Optional<CompliancePolicy> getPolicyById(UUID id) {
        return policyRepository.findById(id);
    }
    
    @Transactional
    public CompliancePolicy createPolicy(CompliancePolicy policy, UUID createdBy) {
        policy.setCreatedBy(createdBy);
        policy.setLastReview(LocalDate.now());

        if (policy.getCategory() == null || policy.getCategory().isBlank()) {
            policy.setCategory("General");
        }
        if (policy.getType() == null || policy.getType().isBlank()) {
            policy.setType("policy");
        }
        
        // Auto-set next review if frequency is specified
        if (policy.getReviewFrequencyDays() != null && policy.getReviewFrequencyDays() > 0) {
            policy.setNextReview(LocalDate.now().plusDays(policy.getReviewFrequencyDays()));
        }
        
        CompliancePolicy savedPolicy = policyRepository.save(policy);
        
        // Create pending submissions for all applicable users
        createPendingSubmissionsForPolicy(savedPolicy);
        
        return savedPolicy;
    }
    
    @Transactional
    public CompliancePolicy updatePolicy(UUID id, CompliancePolicy updatedPolicy) {
        return policyRepository.findById(id)
                .map(policy -> {
                    policy.setTitle(updatedPolicy.getTitle());
                    String category = updatedPolicy.getCategory();
                    policy.setCategory(category != null && !category.isBlank() ? category : (policy.getCategory() != null ? policy.getCategory() : "General"));
                    policy.setDepartment(updatedPolicy.getDepartment());
                    policy.setDescription(updatedPolicy.getDescription());
                    String type = updatedPolicy.getType();
                    policy.setType(type != null && !type.isBlank() ? type : (policy.getType() != null ? policy.getType() : "policy"));
                    policy.setFileUrl(updatedPolicy.getFileUrl());
                    policy.setFileName(updatedPolicy.getFileName());
                    policy.setDeadline(updatedPolicy.getDeadline());
                    policy.setReviewFrequencyDays(updatedPolicy.getReviewFrequencyDays());
                    policy.setIsActive(updatedPolicy.getIsActive());
                    return policyRepository.save(policy);
                })
                .orElseThrow(() -> new RuntimeException("Policy not found"));
    }
    
    @Transactional
    public void deletePolicy(UUID id) {
        policyRepository.findById(id).ifPresent(policy -> {
            policy.setIsActive(false);
            policyRepository.save(policy);
        });
    }
    
    // ==================== EMPLOYEE COMPLIANCE ITEMS ====================
    
    public List<Map<String, Object>> getMyComplianceItems(UUID userId) {
        Profile profile = profileRepository.findById(userId).orElse(null);
        String department = profile != null ? profile.getDepartment() : null;
        
        List<CompliancePolicy> applicablePolicies;
        if (department != null) {
            applicablePolicies = policyRepository.findActivePoliciesForDepartment(department);
        } else {
            applicablePolicies = policyRepository.findGlobalActivePolicies();
        }
        
        List<Map<String, Object>> items = new ArrayList<>();
        
        for (CompliancePolicy policy : applicablePolicies) {
            Optional<ComplianceSubmission> submission = 
                    submissionRepository.findByPolicyIdAndUserId(policy.getId(), userId);
            
            String status = submission.map(ComplianceSubmission::getStatus).orElse("pending");
            
            // Only include if not already approved
            if (!"approved".equals(status)) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", policy.getId());
                item.put("policyId", policy.getId());
                item.put("title", policy.getTitle());
                item.put("description", policy.getDescription());
                item.put("type", policy.getType());
                item.put("category", policy.getCategory());
                item.put("deadline", policy.getDeadline());
                item.put("status", status);
                item.put("submissionId", submission.map(ComplianceSubmission::getId).orElse(null));
                items.add(item);
            }
        }
        
        return items;
    }
    
    @Transactional
    public ComplianceSubmission submitCompliance(UUID policyId, UUID userId, Map<String, Object> data) {
        CompliancePolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        
        ComplianceSubmission submission = submissionRepository
                .findByPolicyIdAndUserId(policyId, userId)
                .orElseGet(() -> {
                    ComplianceSubmission newSubmission = new ComplianceSubmission();
                    newSubmission.setPolicy(policy);
                    newSubmission.setUserId(userId);
                    return newSubmission;
                });
        
        String type = (String) data.get("type");
        
        if ("policy".equals(type)) {
            // Policy acknowledgement
            submission.setAcknowledged(true);
            submission.setStatus("approved"); // Auto-approve acknowledgements
        } else if ("upload".equals(type)) {
            // Document upload - needs review
            submission.setFileUrl((String) data.get("fileUrl"));
            submission.setFileName((String) data.get("fileName"));
            submission.setStatus("submitted");
        } else if ("training".equals(type)) {
            // Training completion
            submission.setStatus("submitted");
        }
        
        submission.setSubmittedAt(OffsetDateTime.now());
        
        return submissionRepository.save(submission);
    }
    
    // ==================== ADMIN TRACKING ====================
    
    public Map<String, Object> getComplianceMetrics() {
        List<CompliancePolicy> policies = policyRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        
        int totalPolicies = policies.size();
        int compliantPolicies = 0;
        int atRiskPolicies = 0;
        int nonCompliantPolicies = 0;
        long totalStaff = profileRepository.count();
        long totalCompliant = 0;
        
        for (CompliancePolicy policy : policies) {
            long compliant = submissionRepository.countCompliantByPolicy(policy.getId());
            long total = submissionRepository.countTotalByPolicy(policy.getId());
            
            if (total == 0) total = totalStaff; // New policy, all pending
            
            double rate = (total > 0) ? (compliant * 100.0 / total) : 0;
            
            if (rate >= 90) {
                compliantPolicies++;
            } else if (rate >= 70) {
                atRiskPolicies++;
            } else {
                nonCompliantPolicies++;
            }
            
            totalCompliant += compliant;
        }
        
        double overallRate = (totalPolicies > 0 && totalStaff > 0) 
                ? (totalCompliant * 100.0 / (totalPolicies * totalStaff)) 
                : 0;
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("overallComplianceRate", Math.round(overallRate));
        metrics.put("totalPolicies", totalPolicies);
        metrics.put("compliantPolicies", compliantPolicies);
        metrics.put("atRiskPolicies", atRiskPolicies);
        metrics.put("nonCompliantPolicies", nonCompliantPolicies);
        
        return metrics;
    }
    
    public List<Map<String, Object>> getPolicyDetails() {
        List<CompliancePolicy> policies = policyRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        long totalStaff = profileRepository.count();
        
        return policies.stream().map(policy -> {
            long compliant = submissionRepository.countCompliantByPolicy(policy.getId());
            long total = submissionRepository.countTotalByPolicy(policy.getId());
            
            if (total == 0) total = totalStaff;
            
            double rate = (total > 0) ? (compliant * 100.0 / total) : 0;
            String status = rate >= 90 ? "Compliant" : (rate >= 70 ? "At Risk" : "Non-Compliant");
            
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", policy.getId());
            detail.put("title", policy.getTitle());
            detail.put("category", policy.getCategory());
            detail.put("department", policy.getDepartment());
            detail.put("description", policy.getDescription());
            detail.put("type", policy.getType());
            detail.put("fileUrl", policy.getFileUrl());
            detail.put("fileName", policy.getFileName());
            detail.put("status", status);
            detail.put("complianceRate", Math.round(rate));
            detail.put("staffCount", total);
            detail.put("nonCompliant", total - compliant);
            detail.put("lastReview", policy.getLastReview());
            detail.put("nextReview", policy.getNextReview());
            
            return detail;
        }).collect(Collectors.toList());
    }
    
    public List<ComplianceSubmission> getPolicySubmissions(UUID policyId) {
        return submissionRepository.findByPolicyIdOrderByCreatedAtDesc(policyId);
    }
    
    @Transactional
    public ComplianceSubmission reviewSubmission(UUID submissionId, UUID reviewerId, String status, String notes) {
        return submissionRepository.findById(submissionId)
                .map(submission -> {
                    submission.setStatus(status);
                    submission.setReviewedBy(reviewerId);
                    submission.setReviewedAt(OffsetDateTime.now());
                    submission.setReviewNotes(notes);
                    return submissionRepository.save(submission);
                })
                .orElseThrow(() -> new RuntimeException("Submission not found"));
    }
    
    // ==================== HELPERS ====================
    
    private void createPendingSubmissionsForPolicy(CompliancePolicy policy) {
        List<Profile> applicableProfiles;
        
        if (policy.getDepartment() != null && !policy.getDepartment().isEmpty()) {
            applicableProfiles = profileRepository.findByDepartment(policy.getDepartment());
        } else {
            applicableProfiles = profileRepository.findAll();
        }
        
        for (Profile profile : applicableProfiles) {
            // Skip admins
            if ("admin".equalsIgnoreCase(profile.getRole())) {
                continue;
            }
            
            // Check if submission already exists
            if (submissionRepository.findByPolicyIdAndUserId(policy.getId(), profile.getId()).isEmpty()) {
                ComplianceSubmission submission = new ComplianceSubmission();
                submission.setPolicy(policy);
                submission.setUserId(profile.getId());
                submission.setStatus("pending");
                submissionRepository.save(submission);
            }
        }
    }
}
