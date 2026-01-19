package com.schoolable.backend.hr;

import com.schoolable.backend.performance.TrainingRecord;
import com.schoolable.backend.performance.TrainingRecordRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for HR Management operations.
 */
@RestController
@RequestMapping("/api/hr")
public class HRManagementController {

    @Autowired
    private HRManagementService hrService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private TrainingRecordRepository trainingRecordRepository;

    @Autowired
    private JobLevelRepository jobLevelRepository;

    // =====================================================
    // ORGANIZATIONAL STRUCTURE
    // =====================================================

    /**
     * Get organizational structure by grades.
     */
    @GetMapping("/structure")
    public ResponseEntity<?> getOrganizationalStructure() {
        return ResponseEntity.ok(hrService.getOrganizationalStructure());
    }

    /**
     * Get all job levels.
     */
    @GetMapping("/job-levels")
    public ResponseEntity<?> getJobLevels() {
        List<JobLevel> levels = hrService.getAllJobLevels();
        return ResponseEntity.ok(levels.stream().map(level -> Map.of(
            "id", level.getId(),
            "levelNumber", level.getLevelNumber(),
            "title", level.getTitle(),
            "grade", level.getGrade(),
            "gradeDescription", level.getGradeDescription(),
            "description", level.getDescription() != null ? level.getDescription() : "",
            "minYearsExperience", level.getMinYearsExperience(),
            "maxYearsExperience", level.getMaxYearsExperience(),
            "isTeamLeadEligible", level.getIsTeamLeadEligible()
        )).collect(Collectors.toList()));
    }

    /**
     * Get employees by grade.
     */
    @GetMapping("/structure/grade/{grade}")
    public ResponseEntity<?> getEmployeesByGrade(@PathVariable Integer grade) {
        List<Profile> employees = profileRepository.findByGradeOrderByFullNameAsc(grade);
        return ResponseEntity.ok(employees.stream().map(this::toEmployeeDto).collect(Collectors.toList()));
    }

    /**
     * Get employees by job level.
     */
    @GetMapping("/structure/level/{level}")
    public ResponseEntity<?> getEmployeesByLevel(@PathVariable Integer level) {
        List<Profile> employees = profileRepository.findByJobLevelOrderByFullNameAsc(level);
        return ResponseEntity.ok(employees.stream().map(this::toEmployeeDto).collect(Collectors.toList()));
    }

    // =====================================================
    // TEAM LEADS
    // =====================================================

    /**
     * Get all team leads.
     */
    @GetMapping("/team-leads")
    public ResponseEntity<?> getTeamLeads() {
        return ResponseEntity.ok(hrService.getActiveTeamLeads());
    }

    /**
     * Appoint a new team lead.
     */
    @PostMapping("/team-leads")
    public ResponseEntity<?> appointTeamLead(
            Authentication auth,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        UUID employeeId = UUID.fromString((String) request.get("employeeId"));
        String teamName = (String) request.get("teamName");

        try {
            TeamLeadAppointment appointment = hrService.appointTeamLead(employeeId, teamName, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team lead appointed successfully",
                "appointmentId", appointment.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get pending team lead requests.
     */
    @GetMapping("/team-leads/requests")
    public ResponseEntity<?> getPendingTeamLeadRequests() {
        return ResponseEntity.ok(hrService.getPendingTeamLeadRequests());
    }

    /**
     * Approve a pending team lead request.
     */
    @PostMapping("/team-leads/requests/{employeeId}/approve")
    public ResponseEntity<?> approveTeamLeadRequest(
            Authentication auth,
            @PathVariable UUID employeeId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        String teamName = request != null ? (String) request.get("teamName") : null;

        try {
            TeamLeadAppointment appointment = hrService.approveTeamLeadRequest(employeeId, teamName, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team lead approved successfully",
                "appointmentId", appointment.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Reject a pending team lead request.
     */
    @PostMapping("/team-leads/requests/{employeeId}/reject")
    public ResponseEntity<?> rejectTeamLeadRequest(
            Authentication auth,
            @PathVariable UUID employeeId,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        String reason = request != null ? (String) request.get("reason") : null;

        try {
            hrService.rejectTeamLeadRequest(employeeId, userId, reason);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team lead request rejected"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // =====================================================
    // TEAMS
    // =====================================================

    /**
     * Get all teams.
     */
    @GetMapping("/teams")
    public ResponseEntity<?> getTeams() {
        return ResponseEntity.ok(hrService.getTeams());
    }

    /**
     * Create a new team.
     */
    @PostMapping("/teams")
    public ResponseEntity<?> createTeam(
            Authentication auth,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        String name = request.get("name") != null ? request.get("name").toString() : null;
        String description = request.get("description") != null ? request.get("description").toString() : null;

        try {
            Team team = hrService.createTeam(name, description, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team created successfully",
                "teamId", team.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // =====================================================
    // PROBATION
    // =====================================================

    /**
     * Get all probation records.
     */
    @GetMapping("/probation")
    public ResponseEntity<?> getProbations() {
        return ResponseEntity.ok(hrService.getAllProbations());
    }

    /**
     * Get probation statistics.
     */
    @GetMapping("/probation/stats")
    public ResponseEntity<?> getProbationStats() {
        return ResponseEntity.ok(hrService.getProbationStats());
    }

    /**
     * Create a new probation record.
     */
    @PostMapping("/probation")
    public ResponseEntity<?> createProbation(
            Authentication auth,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        UUID employeeId = UUID.fromString((String) request.get("employeeId"));
        LocalDate startDate = LocalDate.parse((String) request.get("startDate"));
        int months = request.get("probationMonths") != null ? 
            ((Number) request.get("probationMonths")).intValue() : 6;
        UUID supervisorId = request.get("supervisorId") != null ? 
            UUID.fromString((String) request.get("supervisorId")) : null;

        try {
            ProbationRecord record = hrService.createProbation(employeeId, startDate, months, supervisorId, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Probation record created",
                "probationId", record.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Submit probation appraisal.
     */
    @PostMapping("/probation/{id}/appraisal")
    public ResponseEntity<?> submitAppraisal(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        BigDecimal score = new BigDecimal(request.get("score").toString());
        String recommendation = (String) request.get("recommendation");
        String notes = (String) request.get("notes");

        try {
            ProbationRecord record = hrService.submitAppraisal(id, score, recommendation, notes, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Appraisal submitted",
                "status", record.getStatus(),
                "policyRecommendation", record.getPolicyRecommendation()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Confirm employee after probation.
     */
    @PostMapping("/probation/{id}/confirm")
    public ResponseEntity<?> confirmEmployee(
            Authentication auth,
            @PathVariable UUID id
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());

        try {
            hrService.confirmEmployee(id, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Employee confirmed successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // =====================================================
    // PIP
    // =====================================================

    /**
     * Get all active PIPs.
     */
    @GetMapping("/pip")
    public ResponseEntity<?> getPips() {
        return ResponseEntity.ok(hrService.getActivePips());
    }

    /**
     * Create a new PIP.
     */
    @PostMapping("/pip")
    public ResponseEntity<?> createPip(
            Authentication auth,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        UUID employeeId = UUID.fromString((String) request.get("employeeId"));
        String reason = (String) request.get("reason");
        BigDecimal triggerScore = request.get("triggerScore") != null ?
            new BigDecimal(request.get("triggerScore").toString()) : null;
        String quarter = (String) request.get("quarter");
        Integer year = request.get("year") != null ? ((Number) request.get("year")).intValue() : null;
        UUID supervisorId = request.get("supervisorId") != null ?
            UUID.fromString((String) request.get("supervisorId")) : null;

        try {
            PipRecord pip = hrService.createPip(employeeId, reason, triggerScore, quarter, year, supervisorId, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "PIP created successfully",
                "pipId", pip.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Complete a PIP.
     */
    @PostMapping("/pip/{id}/complete")
    public ResponseEntity<?> completePip(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, Object> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        BigDecimal finalScore = new BigDecimal(request.get("finalScore").toString());
        String notes = (String) request.get("notes");
        String outcome = (String) request.get("outcome");

        try {
            PipRecord pip = hrService.completePip(id, finalScore, notes, outcome, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "PIP completed",
                "status", pip.getStatus(),
                "outcome", pip.getOutcome()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // =====================================================
    // PROMOTIONS
    // =====================================================

    /**
     * Get promotion eligibility list.
     */
    @GetMapping("/promotions/eligible")
    public ResponseEntity<?> getPromotionEligibility() {
        return ResponseEntity.ok(hrService.getPromotionEligibility());
    }

    /**
     * Get promotion thresholds (policy info).
     */
    @GetMapping("/promotions/thresholds")
    public ResponseEntity<?> getPromotionThresholds() {
        return ResponseEntity.ok(Map.of(
            "vertical", Map.of(
                "cgpaThreshold", 4.20,
                "quarterlyMin", 3.70,
                "description", "Move to a higher level job with more authority"
            ),
            "horizontal", Map.of(
                "cgpaThreshold", 3.50,
                "description", "Move to different role at same level with wider skills/scope"
            ),
            "fastTrack", Map.of(
                "cgpaThreshold", 4.60,
                "consecutiveQuarters", 2,
                "description", "Immediate promotion review for exceptional performance"
            )
        ));
    }

    // =====================================================
    // CERTIFICATES / TRAINING
    // =====================================================

    /**
     * Get all training records for admin view.
     */
    @GetMapping("/certificates")
    public ResponseEntity<?> getAllCertificates() {
        List<TrainingRecord> records = trainingRecordRepository.findAllByOrderByCreatedAtDesc();
        
        List<Map<String, Object>> result = records.stream().map(record -> {
            Profile employee = profileRepository.findById(record.getEmployeeId()).orElse(null);
            
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", record.getId());
            dto.put("employeeId", record.getEmployeeId());
            dto.put("employeeName", employee != null ? employee.getFullName() : "Unknown");
            dto.put("employeeDepartment", employee != null ? employee.getDepartment() : null);
            dto.put("certificateName", record.getCertificateName());
            dto.put("provider", record.getProvider());
            dto.put("completedAt", record.getCompletedAt());
            dto.put("expiresAt", record.getExpiresAt());
            dto.put("quarter", record.getQuarter());
            dto.put("year", record.getYear());
            dto.put("status", record.getStatus());
            dto.put("fileUrl", record.getFileUrl());
            dto.put("reviewedAt", record.getReviewedAt());
            dto.put("reviewNotes", record.getReviewNotes());
            
            return dto;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get certificate statistics.
     */
    @GetMapping("/certificates/stats")
    public ResponseEntity<?> getCertificateStats() {
        long total = trainingRecordRepository.count();
        long pending = trainingRecordRepository.countByStatus("pending");
        long approved = trainingRecordRepository.countByStatus("approved");
        long rejected = trainingRecordRepository.countByStatus("rejected");

        return ResponseEntity.ok(Map.of(
            "total", total,
            "pending", pending,
            "approved", approved,
            "rejected", rejected
        ));
    }

    /**
     * Approve or reject a certificate.
     */
    @PatchMapping("/certificates/{id}/review")
    public ResponseEntity<?> reviewCertificate(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {
        UUID userId = UUID.fromString((String) auth.getPrincipal());
        String status = request.get("status");
        String notes = request.get("notes");

        Optional<TrainingRecord> optional = trainingRecordRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TrainingRecord record = optional.get();
        record.setStatus(status);
        record.setReviewNotes(notes);
        record.setReviewedBy(userId);
        record.setReviewedAt(java.time.OffsetDateTime.now());
        
        trainingRecordRepository.save(record);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Certificate " + status
        ));
    }

    /**
     * Seed HR data for testing.
     */
    @PostMapping("/seed")
    public ResponseEntity<?> seedHRData() {
        hrService.seedHRData();
        return ResponseEntity.ok(Map.of("message", "HR Data seeded successfully"));
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private Map<String, Object> toEmployeeDto(Profile profile) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", profile.getId());
        dto.put("name", profile.getFullName());
        dto.put("email", profile.getEmail());
        dto.put("role", profile.getJobTitle());
        dto.put("department", profile.getDepartment());
        dto.put("jobLevel", profile.getJobLevel());
        dto.put("grade", profile.getGrade());
        dto.put("isTeamLead", profile.getIsTeamLead());
        dto.put("status", profile.getStatus());
        dto.put("avatar", "https://api.dicebear.com/7.x/avataaars/svg?seed=" + profile.getId());
        return dto;
    }
}
