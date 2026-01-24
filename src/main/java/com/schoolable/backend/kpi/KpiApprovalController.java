package com.schoolable.backend.kpi;

import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KPI Approval Controller
 * Handles approval workflow for KPIs: DRAFT → PENDING_APPROVAL → ACTIVE/REJECTED
 * Super Admins and HR can approve/reject KPIs.
 */
@RestController
@RequestMapping({"/api/kpi-approval", "/kpi-approval"})
@Tag(name = "KPI Approval", description = "KPI approval workflow for admins")
public class KpiApprovalController {

    @Autowired
    private IndividualKpiRepository individualKpiRepository;

    @Autowired
    private TeamKpiRepository teamKpiRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private KpiHistoryRepository kpiHistoryRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Get all KPIs pending approval
     */
    @GetMapping("/pending")
    @Operation(summary = "Get pending KPIs", description = "Get all KPIs awaiting approval")
    public ResponseEntity<?> getPendingKpis(
            @RequestParam(required = false) String department,
            Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        List<IndividualKpi> pending = individualKpiRepository
            .findByApprovalStatus(IndividualKpi.ApprovalStatus.PENDING_APPROVAL);
        
        if (department != null && !department.isEmpty()) {
            pending = pending.stream()
                .filter(k -> department.equals(k.getDepartment()))
                .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = pending.stream()
            .map(this::buildKpiResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "pendingCount", result.size(),
            "kpis", result
        ));
    }

    /**
     * Submit KPI for approval
     */
    @PostMapping("/submit/{kpiId}")
    @Operation(summary = "Submit KPI for approval")
    public ResponseEntity<?> submitForApproval(@PathVariable UUID kpiId, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        
        IndividualKpi kpi = individualKpiRepository.findById(kpiId).orElse(null);
        if (kpi == null) {
            return ResponseEntity.status(404).body(Map.of("error", "KPI not found"));
        }

        // Check if user owns the KPI or is the team lead
        if (!kpi.getSetById().equals(userId) && !kpi.getEmployeeId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }

        if (kpi.getApprovalStatus() != IndividualKpi.ApprovalStatus.DRAFT) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "KPI is not in DRAFT status"));
        }

        kpi.submitForApproval();
        individualKpiRepository.save(kpi);

        // Log the submission
        logKpiChange(kpi.getId(), "individual", userId, 
            "DRAFT", "PENDING_APPROVAL", "Submitted for approval");

        notifyAdminsKpiSubmission(kpi);

        return ResponseEntity.ok(buildKpiResponse(kpi));
    }

    /**
     * Approve a KPI
     */
    @PostMapping("/approve/{kpiId}")
    @Operation(summary = "Approve KPI", description = "Approve a pending KPI (admin only)")
    public ResponseEntity<?> approveKpi(@PathVariable UUID kpiId, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        UUID approverId = (UUID) auth.getPrincipal();
        
        IndividualKpi kpi = individualKpiRepository.findById(kpiId).orElse(null);
        if (kpi == null) {
            return ResponseEntity.status(404).body(Map.of("error", "KPI not found"));
        }

        if (kpi.getApprovalStatus() != IndividualKpi.ApprovalStatus.PENDING_APPROVAL) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "KPI is not pending approval"));
        }

        String previousStatus = kpi.getApprovalStatus().name();
        kpi.approve(approverId);
        individualKpiRepository.save(kpi);

        // Log the approval
        logKpiChange(kpi.getId(), "individual", approverId, 
            previousStatus, "ACTIVE", "Approved by admin");

        notifyKpiDecision(kpi, "approved");

        return ResponseEntity.ok(Map.of(
            "message", "KPI approved successfully",
            "kpi", buildKpiResponse(kpi)
        ));
    }

    /**
     * Reject a KPI
     */
    @PostMapping("/reject/{kpiId}")
    @Operation(summary = "Reject KPI", description = "Reject a pending KPI with reason")
    public ResponseEntity<?> rejectKpi(
            @PathVariable UUID kpiId,
            @RequestBody RejectionRequest req,
            Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        UUID approverId = (UUID) auth.getPrincipal();
        
        IndividualKpi kpi = individualKpiRepository.findById(kpiId).orElse(null);
        if (kpi == null) {
            return ResponseEntity.status(404).body(Map.of("error", "KPI not found"));
        }

        if (kpi.getApprovalStatus() != IndividualKpi.ApprovalStatus.PENDING_APPROVAL) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "KPI is not pending approval"));
        }

        if (req.reason == null || req.reason.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Rejection reason is required"));
        }

        String previousStatus = kpi.getApprovalStatus().name();
        kpi.reject(approverId, req.reason);
        individualKpiRepository.save(kpi);

        // Log the rejection
        logKpiChange(kpi.getId(), "individual", approverId, 
            previousStatus, "REJECTED", "Rejected: " + req.reason);

        notifyKpiDecision(kpi, "rejected");

        return ResponseEntity.ok(Map.of(
            "message", "KPI rejected",
            "kpi", buildKpiResponse(kpi)
        ));
    }

    /**
     * Bulk approve multiple KPIs
     */
    @PostMapping("/approve-bulk")
    @Operation(summary = "Bulk approve KPIs")
    public ResponseEntity<?> bulkApprove(@RequestBody BulkApprovalRequest req, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        UUID approverId = (UUID) auth.getPrincipal();
        
        if (req.kpiIds == null || req.kpiIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No KPI IDs provided"));
        }

        int approved = 0;
        int failed = 0;

        for (UUID kpiId : req.kpiIds) {
            IndividualKpi kpi = individualKpiRepository.findById(kpiId).orElse(null);
            if (kpi != null && kpi.getApprovalStatus() == IndividualKpi.ApprovalStatus.PENDING_APPROVAL) {
                kpi.approve(approverId);
                individualKpiRepository.save(kpi);
                logKpiChange(kpi.getId(), "individual", approverId, 
                    "PENDING_APPROVAL", "ACTIVE", "Bulk approved");
                approved++;
            } else {
                failed++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "approved", approved,
            "failed", failed,
            "message", approved + " KPIs approved, " + failed + " failed"
        ));
    }

    /**
     * Get approval history for a KPI
     */
    @GetMapping("/history/{kpiId}")
    @Operation(summary = "Get KPI approval history")
    public ResponseEntity<?> getKpiHistory(@PathVariable UUID kpiId) {
        List<KpiHistory> history = kpiHistoryRepository.findByKpiIdOrderByChangedAtDesc(kpiId);
        
        List<Map<String, Object>> result = history.stream()
            .map(h -> {
                Profile changer = h.getChangedBy() != null ? 
                    profileRepository.findById(h.getChangedBy()).orElse(null) : null;
                
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("changedAt", h.getChangedAt().toString());
                item.put("previousValue", h.getPreviousValue());
                item.put("newValue", h.getNewValue());
                item.put("changedBy", changer != null ? changer.getFullName() : "System");
                item.put("reason", h.getChangeReason());
                return item;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("history", result));
    }

    /**
     * Get dashboard stats for approval queue
     */
    @GetMapping("/stats")
    @Operation(summary = "Get approval queue stats")
    public ResponseEntity<?> getApprovalStats(Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        long pendingCount = individualKpiRepository.countByApprovalStatus(
            IndividualKpi.ApprovalStatus.PENDING_APPROVAL);
        long draftCount = individualKpiRepository.countByApprovalStatus(
            IndividualKpi.ApprovalStatus.DRAFT);
        long activeCount = individualKpiRepository.countByApprovalStatus(
            IndividualKpi.ApprovalStatus.ACTIVE);

        return ResponseEntity.ok(Map.of(
            "pending", pendingCount,
            "draft", draftCount,
            "active", activeCount
        ));
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    // Helper methods
    private Map<String, Object> buildKpiResponse(IndividualKpi kpi) {
        Profile employee = profileRepository.findById(kpi.getEmployeeId()).orElse(null);
        Profile setter = profileRepository.findById(kpi.getSetById()).orElse(null);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", kpi.getId().toString());
        result.put("name", kpi.getName());
        result.put("description", kpi.getDescription());
        result.put("targetValue", kpi.getTargetValue());
        result.put("targetUnit", kpi.getTargetUnit());
        result.put("currentValue", kpi.getCurrentValue());
        result.put("achievementPercentage", kpi.getAchievementPercentage());
        result.put("weight", kpi.getWeight());
        result.put("quarter", kpi.getQuarter());
        result.put("year", kpi.getYear());
        result.put("department", kpi.getDepartment());
        result.put("approvalStatus", kpi.getApprovalStatus().name());
        result.put("submittedAt", kpi.getSubmittedAt() != null ? kpi.getSubmittedAt().toString() : null);
        
        if (employee != null) {
            result.put("employee", Map.of(
                "id", employee.getId().toString(),
                "name", employee.getFullName()
            ));
        }
        if (setter != null) {
            result.put("setBy", Map.of(
                "id", setter.getId().toString(),
                "name", setter.getFullName()
            ));
        }
        
        // Cascading info
        if (kpi.getParentKpiId() != null) {
            result.put("cascading", Map.of(
                "parentId", kpi.getParentKpiId().toString(),
                "level", kpi.getCascadeLevel(),
                "source", kpi.getCascadeSource()
            ));
        }

        return result;
    }

    private void logKpiChange(UUID kpiId, String kpiType, UUID changedBy, 
                              String previousValue, String newValue, String reason) {
        KpiHistory history = new KpiHistory(
            kpiId, kpiType, previousValue, newValue, changedBy, reason, "approval_status"
        );
        kpiHistoryRepository.save(history);
    }

    // Request DTOs
    public static class RejectionRequest {
        public String reason;
    }

    public static class BulkApprovalRequest {
        public List<UUID> kpiIds;
    }

    private void notifyAdminsKpiSubmission(IndividualKpi kpi) {
        List<UUID> adminIds = profileRepository.findAll().stream()
            .filter(profile -> isAdminRole(profile.getRole()))
            .filter(profile -> isActiveStatus(profile.getStatus()))
            .map(Profile::getId)
            .toList();

        if (adminIds.isEmpty()) {
            return;
        }

        String title = "KPI Approval";
        String body = "A KPI is awaiting approval.";
        Map<String, Object> data = new HashMap<>();
        data.put("kpiId", kpi.getId().toString());
        data.put("action", "review_kpi");

        notificationService.sendToUsers(
            adminIds,
            title,
            body,
            NotificationService.TYPE_PERFORMANCE,
            kpi.getId().toString(),
            data
        );
    }

    private void notifyKpiDecision(IndividualKpi kpi, String decision) {
        if (kpi.getEmployeeId() == null) {
            return;
        }
        String title = "KPI Update";
        String body = "Your KPI was " + decision + ".";
        Map<String, Object> data = new HashMap<>();
        data.put("kpiId", kpi.getId().toString());
        data.put("action", "open_kpi");

        notificationService.sendToUser(
            kpi.getEmployeeId(),
            title,
            body,
            NotificationService.TYPE_PERFORMANCE,
            kpi.getId().toString(),
            data
        );
    }

    private boolean isAdminRole(String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("admin")
            || normalized.equals("super_admin")
            || normalized.equals("super admin")
            || normalized.equals("superadmin");
    }

    private boolean isActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalized = status.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("active") || normalized.equals("approved");
    }
}
