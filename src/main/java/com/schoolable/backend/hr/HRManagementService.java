package com.schoolable.backend.hr;

import com.schoolable.backend.audit.AuditService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for HR Management operations.
 * Implements Allpro Technologies Performance & Employment Level Cadre Policy.
 */
@Service
public class HRManagementService {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private JobLevelRepository jobLevelRepository;

    @Autowired
    private ProbationRepository probationRepository;

    @Autowired
    private PipRepository pipRepository;

    @Autowired
    private TeamLeadRepository teamLeadRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AuditService auditService;

    // =====================================================
    // ORGANIZATIONAL STRUCTURE
    // =====================================================

    /**
     * Get organizational structure by grade with employee counts.
     */
    public List<Map<String, Object>> getOrganizationalStructure() {
        syncGradeAssignments();
        List<Map<String, Object>> structure = new ArrayList<>();
        
        // Define grades based on policy pyramid
        Map<Integer, String[]> gradeDefinitions = new LinkedHashMap<>();
        gradeDefinitions.put(6, new String[]{"Directors", "General Manager"});
        gradeDefinitions.put(5, new String[]{"C-Suite Executives", "Deputy GM, Asst GM"});
        gradeDefinitions.put(4, new String[]{"Senior Executives, Senior Managers, Managers", "Principal Manager, Senior Manager, Manager, Asst Manager"});
        gradeDefinitions.put(3, new String[]{"Junior Executives, Asst. Team Leads, Team Leads", "Senior Associate, Associate, Senior Analyst, Analyst, Senior Officer"});
        gradeDefinitions.put(2, new String[]{"NYSC, Internship, Mgt Trainees", "Officer, Executive Trainee"});
        gradeDefinitions.put(1, new String[]{"Auxiliary & Contract Staff", "Contract Staff, SIWES, IT"});

        for (Map.Entry<Integer, String[]> entry : gradeDefinitions.entrySet()) {
            int grade = entry.getKey();
            String[] info = entry.getValue();
            
            List<Profile> employeesInGrade = profileRepository.findByGradeOrderByFullNameAsc(grade);
            
            List<Map<String, Object>> employees = employeesInGrade.stream()
                .map(this::toEmployeeDto)
                .collect(Collectors.toList());
            
            Map<String, Object> gradeData = new LinkedHashMap<>();
            gradeData.put("grade", grade);
            gradeData.put("title", info[0]);
            gradeData.put("roles", info[1]);
            gradeData.put("count", employees.size());
            gradeData.put("employees", employees);
            
            structure.add(gradeData);
        }
        
        return structure;
    }

    private void syncGradeAssignments() {
        List<Profile> profiles = profileRepository.findAll();
        if (profiles.isEmpty()) {
            return;
        }

        for (Profile profile : profiles) {
            boolean changed = false;
            Integer jobLevel = profile.getJobLevel();
            if (jobLevel != null) {
                Optional<JobLevel> jobLevelOpt = jobLevelRepository.findByLevelNumber(jobLevel);
                if (jobLevelOpt.isPresent()) {
                    Integer desiredGrade = jobLevelOpt.get().getGrade();
                    if (!Objects.equals(profile.getGrade(), desiredGrade)) {
                        profile.setGrade(desiredGrade);
                        changed = true;
                    }
                }
            } else if (Boolean.TRUE.equals(profile.getIsTeamLead())) {
                Integer grade = profile.getGrade();
                if (grade == null || grade < 3) {
                    profile.setGrade(3);
                    changed = true;
                }
            }

            if (changed) {
                profileRepository.save(profile);
            }
        }
    }

    /**
     * Get employees by job level.
     */
    public List<Map<String, Object>> getEmployeesByLevel(Integer level) {
        List<Profile> employees = profileRepository.findByJobLevelOrderByFullNameAsc(level);
        return employees.stream().map(this::toEmployeeDto).collect(Collectors.toList());
    }

    /**
     * Get all job levels.
     */
    public List<JobLevel> getAllJobLevels() {
        return jobLevelRepository.findAllByOrderByLevelNumberAsc();
    }

    // =====================================================
    // TEAM LEADS
    // =====================================================

    /**
     * Get all active team leads with details.
     */
    public List<Map<String, Object>> getActiveTeamLeads() {
        // Get from team_lead_appointments table
        List<TeamLeadAppointment> appointments = teamLeadRepository.findActiveTeamLeads();
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (TeamLeadAppointment appointment : appointments) {
            Profile employee = profileRepository.findById(appointment.getEmployeeId()).orElse(null);
            if (employee == null) continue;
            
            Map<String, Object> lead = new LinkedHashMap<>();
            lead.put("id", employee.getId());
            lead.put("name", employee.getFullName());
            lead.put("email", employee.getEmail());
            lead.put("role", employee.getJobTitle());
            lead.put("department", employee.getDepartment());
            lead.put("employeeId", employee.getEmployeeId());
            lead.put("gender", employee.getGender());
            lead.put("appointmentId", appointment.getId());
            lead.put("status", appointment.getStatus());
            lead.put("appointedAt", appointment.getAppointedAt());
            lead.put("confirmedAt", appointment.getConfirmedAt());
            lead.put("teamName", appointment.getTeamName());
            lead.put("teamSize", appointment.getTeamSize());
            lead.put("reviewCycles", appointment.getReviewCyclesCompleted());
            lead.put("cgpaAtAppointment", appointment.getCgpaAtAppointment());
            lead.put("currentCgpa", appointment.getCurrentCgpa());
            lead.put("perks", appointment.getPerks());
            lead.put("monthsAsLead", appointment.getMonthsAsTeamLead());
            lead.put("requestStatus", employee.getTeamLeadRequestStatus());
            lead.put("requestedAt", employee.getTeamLeadRequestedAt());
            
            result.add(lead);
        }
        
        // Also include profiles marked as team_lead but without appointments
        List<Profile> teamLeadProfiles = profileRepository.findByIsTeamLeadTrue();
        Set<UUID> appointedIds = appointments.stream()
            .map(TeamLeadAppointment::getEmployeeId)
            .collect(Collectors.toSet());
        
        for (Profile profile : teamLeadProfiles) {
            if (!appointedIds.contains(profile.getId())) {
                Map<String, Object> lead = toEmployeeDto(profile);
                lead.put("status", "legacy");
                lead.put("teamSize", 0);
                lead.put("reviewCycles", 0);
                lead.put("requestStatus", profile.getTeamLeadRequestStatus());
                lead.put("requestedAt", profile.getTeamLeadRequestedAt());
                result.add(lead);
            }
        }
        
        return result;
    }

    /**
     * Get pending team lead requests.
     */
    public List<Map<String, Object>> getPendingTeamLeadRequests() {
        return profileRepository.findByTeamLeadRequestStatus("pending")
            .stream()
            .map(profile -> {
                Map<String, Object> dto = toEmployeeDto(profile);
                dto.put("requestStatus", profile.getTeamLeadRequestStatus());
                dto.put("requestedAt", profile.getTeamLeadRequestedAt());
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Appoint a new team lead.
     */
    @Transactional
    public TeamLeadAppointment appointTeamLead(UUID employeeId, String teamName, UUID appointedBy) {
        Profile employee = profileRepository.findById(employeeId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        
        // Check if already a team lead
        Optional<TeamLeadAppointment> existing = teamLeadRepository
            .findByEmployeeIdAndStatus(employeeId, TeamLeadAppointment.STATUS_ACTING);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Employee is already an acting team lead");
        }
        
        existing = teamLeadRepository.findByEmployeeIdAndStatus(employeeId, TeamLeadAppointment.STATUS_CONFIRMED);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Employee is already a confirmed team lead");
        }
        
        TeamLeadAppointment appointment = new TeamLeadAppointment();
        appointment.setEmployeeId(employeeId);
        appointment.setAppointedAt(OffsetDateTime.now());
        appointment.setStatus(TeamLeadAppointment.STATUS_ACTING);
        appointment.setDepartment(employee.getDepartment());
        String finalTeamName = teamName != null && !teamName.isBlank()
            ? teamName
            : (employee.getDepartment() != null ? employee.getDepartment() : "Team");
        appointment.setTeamName(finalTeamName);
        appointment.setTeamSize(0);
        appointment.setPerks("[\"workspace\", \"data_allowance\"]"); // Default perks
        
        appointment = teamLeadRepository.save(appointment);
        
        // Update profile
        employee.setIsTeamLead(true);
        employee.setTeamLeadRequestStatus("approved");
        profileRepository.save(employee);
        
        // Audit log
        auditService.logCreate("TEAM_LEAD_APPOINTMENT", appointment.getId().toString(), appointedBy);
        
        return appointment;
    }

    /**
     * Approve a pending team lead request and appoint as acting lead.
     */
    @Transactional
    public TeamLeadAppointment approveTeamLeadRequest(UUID employeeId, String teamName, UUID approvedBy) {
        Profile employee = profileRepository.findById(employeeId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        if (!"pending".equalsIgnoreCase(employee.getTeamLeadRequestStatus())) {
            throw new IllegalArgumentException("Team lead request is not pending");
        }

        TeamLeadAppointment appointment = appointTeamLead(
            employeeId,
            teamName != null && !teamName.isBlank() ? teamName : employee.getDepartment(),
            approvedBy
        );

        employee.setTeamLeadRequestStatus("approved");
        profileRepository.save(employee);

        return appointment;
    }

    /**
     * Reject a pending team lead request.
     */
    @Transactional
    public void rejectTeamLeadRequest(UUID employeeId, UUID rejectedBy, String reason) {
        Profile employee = profileRepository.findById(employeeId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        employee.setTeamLeadRequestStatus("rejected");
        profileRepository.save(employee);

        auditService.logUpdate(
            "TEAM_LEAD_REQUEST",
            employeeId.toString(),
            Map.of("status", "rejected", "reason", reason != null ? reason : ""),
            rejectedBy
        );
    }

    // =====================================================
    // TEAMS
    // =====================================================

    /**
     * Get all teams (registered teams plus departments already in use).
     */
    public List<Map<String, Object>> getTeams() {
        Map<String, Team> managedTeams = teamRepository.findAllByOrderByNameAsc()
            .stream()
            .collect(Collectors.toMap(t -> t.getName().toLowerCase(Locale.ROOT), t -> t, (a, b) -> a));

        List<String> departments = profileRepository.findAllDepartments();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Team team : managedTeams.values()) {
            result.add(toTeamDto(team, true));
        }

        for (String dept : departments) {
            if (dept == null || dept.isBlank()) continue;
            String key = dept.toLowerCase(Locale.ROOT);
            if (!managedTeams.containsKey(key)) {
                result.add(Map.of(
                    "id", "department:" + dept,
                    "name", dept,
                    "description", "",
                    "isActive", true,
                    "memberCount", profileRepository.countByDepartment(dept),
                    "managed", false
                ));
            }
        }

        result.sort(Comparator.comparing(t -> ((String) t.get("name")).toLowerCase(Locale.ROOT)));
        return result;
    }

    /**
     * Create a new team.
     */
    @Transactional
    public Team createTeam(String name, String description, UUID createdBy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Team name is required");
        }

        teamRepository.findByNameIgnoreCase(name)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Team already exists");
            });

        Team team = new Team();
        team.setName(name.trim());
        team.setDescription(description);
        team.setIsActive(true);
        team.setCreatedBy(createdBy);

        Team saved = teamRepository.save(team);
        auditService.logCreate("TEAM", saved.getId().toString(), createdBy);
        return saved;
    }

    // =====================================================
    // PROBATION
    // =====================================================

    /**
     * Get all probation records with employee details.
     */
    public List<Map<String, Object>> getAllProbations() {
        List<ProbationRecord> records = probationRepository.findActiveProbations();
        return records.stream().map(this::toProbationDto).collect(Collectors.toList());
    }

    /**
     * Get probation statistics.
     */
    public Map<String, Object> getProbationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("onProbation", probationRepository.countActiveProbations());
        stats.put("dueForConfirmation", probationRepository.findActiveProbations().stream()
            .filter(record -> !record.getCurrentEndDate().isAfter(LocalDate.now()))
            .count());
        stats.put("atRisk", probationRepository.countAtRisk());
        stats.put("overdue", probationRepository.findOverdue(LocalDate.now()).size());
        return stats;
    }

    /**
     * Get PIP statistics with alert-oriented counts.
     */
    public Map<String, Object> getPipStats() {
        Map<String, Object> stats = new HashMap<>();
        List<PipRecord> active = pipRepository.findActivePips();
        long overdue = active.stream().filter(PipRecord::isOverdue).count();
        long dueSoon = active.stream()
            .filter(record -> !record.isOverdue() && record.getDaysRemaining() <= 7)
            .count();

        stats.put("active", pipRepository.countActivePips());
        stats.put("overdue", overdue);
        stats.put("dueSoon", dueSoon);
        return stats;
    }

    /**
     * Create probation record for new hire.
     */
    @Transactional
    public ProbationRecord createProbation(UUID employeeId, LocalDate startDate, 
                                           int probationMonths, UUID supervisorId, UUID createdBy) {
        // Check if already has probation
        if (probationRepository.findByEmployeeId(employeeId).isPresent()) {
            throw new IllegalArgumentException("Employee already has a probation record");
        }
        
        ProbationRecord record = new ProbationRecord();
        record.setEmployeeId(employeeId);
        record.setStartDate(startDate);
        record.setOriginalEndDate(startDate.plusMonths(probationMonths));
        record.setCurrentEndDate(startDate.plusMonths(probationMonths));
        record.setSupervisorId(supervisorId);
        record.setCreatedBy(createdBy);
        
        record = probationRepository.save(record);
        
        // Update profile
        Profile employee = profileRepository.findById(employeeId).orElse(null);
        if (employee != null) {
            employee.setProbationStatus("pending");
            employee.setHireDate(startDate);
            employee.setConfirmationStatus("probation");
            employee.setProbationEndDate(java.sql.Date.valueOf(record.getCurrentEndDate()));
            profileRepository.save(employee);
        }
        
        auditService.logCreate("PROBATION", record.getId().toString(), createdBy);
        
        return record;
    }

    /**
     * Ensure new hires have an auto-created probation record.
     */
    @Transactional
    public Optional<ProbationRecord> ensureProbationForNewHire(Profile employee, UUID createdBy, int probationMonths) {
        if (employee == null || employee.getId() == null) {
            return Optional.empty();
        }
        if ("admin".equalsIgnoreCase(employee.getRole())) {
            return Optional.empty();
        }
        if (probationRepository.findByEmployeeId(employee.getId()).isPresent()) {
            return Optional.empty();
        }

        LocalDate startDate = null;
        if (employee.getHireDate() != null) {
            startDate = employee.getHireDate().toLocalDate();
        } else if (employee.getDateJoined() != null) {
            startDate = employee.getDateJoined().toLocalDate();
        }
        if (startDate == null) {
            startDate = LocalDate.now();
        }

        ProbationRecord record = createProbation(employee.getId(), startDate, probationMonths, null, createdBy);
        return Optional.of(record);
    }

    /**
     * Submit probation appraisal.
     */
    @Transactional
    public ProbationRecord submitAppraisal(UUID probationId, BigDecimal score, 
                                           String recommendation, String notes, UUID submittedBy) {
        ProbationRecord record = probationRepository.findById(probationId)
            .orElseThrow(() -> new IllegalArgumentException("Probation record not found"));
        
        record.setAppraisalCompletedDate(LocalDate.now());
        record.setAppraisalScore(score);
        record.setRecommendation(recommendation);
        record.setRecommendationNotes(notes);
        
        // Determine status based on score and policy
        if (score.compareTo(BigDecimal.valueOf(66)) >= 0) {
            // Score >= 66% - recommend confirmation
            record.setSupervisorApprovedAt(OffsetDateTime.now());
        } else if (score.compareTo(BigDecimal.valueOf(50)) >= 0) {
            // Score 50-65% - extend probation
            int newExtension = record.getExtensionCount() + 1;
            if (newExtension <= 3) {
                record.setExtensionCount(newExtension);
                record.setStatus("extension_" + newExtension);
                record.setCurrentEndDate(record.getCurrentEndDate().plusMonths(1));
            } else {
                record.setRecommendation(ProbationRecord.RECOMMENDATION_TERMINATE);
            }
        } else {
            // Score < 50% - terminate
            record.setRecommendation(ProbationRecord.RECOMMENDATION_TERMINATE);
        }
        
        record = probationRepository.save(record);
        
        auditService.logUpdate("PROBATION", record.getId().toString(), 
            Map.of("appraisalScore", score, "recommendation", recommendation), submittedBy);
        
        return record;
    }

    /**
     * Confirm employee after probation.
     */
    @Transactional
    public ProbationRecord confirmEmployee(UUID probationId, UUID confirmedBy) {
        ProbationRecord record = probationRepository.findById(probationId)
            .orElseThrow(() -> new IllegalArgumentException("Probation record not found"));
        
        record.setStatus(ProbationRecord.STATUS_CONFIRMED);
        record.setConfirmedAt(OffsetDateTime.now());
        record.setCeoApprovedAt(OffsetDateTime.now());
        
        record = probationRepository.save(record);
        
        // Update profile
        Profile employee = profileRepository.findById(record.getEmployeeId()).orElse(null);
        if (employee != null) {
            employee.setProbationStatus("confirmed");
            profileRepository.save(employee);
        }
        
        auditService.logUpdate("PROBATION", record.getId().toString(), 
            Map.of("status", "confirmed"), confirmedBy);
        
        return record;
    }

    // =====================================================
    // PERFORMANCE IMPROVEMENT PLANS (PIP)
    // =====================================================

    /**
     * Get all active PIPs.
     */
    public List<Map<String, Object>> getActivePips() {
        List<PipRecord> records = pipRepository.findActivePips();
        return records.stream().map(this::toPipDto).collect(Collectors.toList());
    }

    /**
     * Create a new PIP.
     */
    @Transactional
    public PipRecord createPip(UUID employeeId, String reason, BigDecimal triggerScore,
                               String quarter, Integer year, UUID supervisorId, UUID createdBy) {
        // Check if already has active PIP
        Optional<PipRecord> existing = pipRepository.findByEmployeeIdAndStatus(employeeId, PipRecord.STATUS_ACTIVE);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Employee already has an active PIP");
        }
        
        PipRecord pip = new PipRecord();
        pip.setEmployeeId(employeeId);
        pip.setStartDate(LocalDate.now());
        pip.setEndDate(LocalDate.now().plusMonths(3)); // Max 3 months per policy
        pip.setTriggerReason(reason);
        pip.setTriggerScore(triggerScore);
        pip.setTriggerQuarter(quarter);
        pip.setTriggerYear(year);
        pip.setSupervisorId(supervisorId);
        pip.setCreatedBy(createdBy);
        
        pip = pipRepository.save(pip);
        
        auditService.logCreate("PIP", pip.getId().toString(), createdBy);
        
        return pip;
    }

    /**
     * Add a goal to a PIP.
     */
    @Transactional
    public PipGoal addPipGoal(Long pipId, String goalDescription, String targetMetric,
                              BigDecimal targetValue, LocalDate dueDate) {
        PipRecord pip = pipRepository.findById(pipId)
            .orElseThrow(() -> new IllegalArgumentException("PIP not found"));
        
        PipGoal goal = new PipGoal();
        goal.setPipRecord(pip);
        goal.setGoalDescription(goalDescription);
        goal.setTargetMetric(targetMetric);
        goal.setTargetValue(targetValue);
        goal.setDueDate(dueDate);
        
        pip.getGoals().add(goal);
        pipRepository.save(pip);
        
        return goal;
    }

    /**
     * Complete a PIP with outcome.
     */
    @Transactional
    public PipRecord completePip(Long pipId, BigDecimal finalScore, String notes, 
                                 String outcome, UUID completedBy) {
        PipRecord pip = pipRepository.findById(pipId)
            .orElseThrow(() -> new IllegalArgumentException("PIP not found"));
        
        pip.setCompletedAt(OffsetDateTime.now());
        pip.setFinalAssessmentScore(finalScore);
        pip.setFinalAssessmentNotes(notes);
        pip.setOutcome(outcome);
        
        if (finalScore.compareTo(BigDecimal.valueOf(50)) >= 0) {
            pip.setStatus(PipRecord.STATUS_COMPLETED_SUCCESS);
        } else {
            pip.setStatus(PipRecord.STATUS_COMPLETED_FAIL);
        }
        
        pip = pipRepository.save(pip);
        
        auditService.logUpdate("PIP", pip.getId().toString(),
            Map.of("outcome", outcome, "finalScore", finalScore), completedBy);
        
        return pip;
    }

    // =====================================================
    // PROMOTIONS
    // =====================================================

    /**
     * Get employees eligible for promotion based on Aura scores (CGPA equivalent).
     */
    public List<Map<String, Object>> getPromotionEligibility() {
        List<Profile> employees = profileRepository.findByStatusAndProbationStatus("active", "confirmed");
        List<Map<String, Object>> eligible = new ArrayList<>();
        
        for (Profile employee : employees) {
            // Calculate CGPA from Aura score (convert from 0-5 scale to CGPA 0-5 scale)
            Double auraScore = getEmployeeAuraScore(employee.getId());
            if (auraScore == null) continue;
            
            BigDecimal cgpa = BigDecimal.valueOf(auraScore);
            
            String eligibilityType = null;
            String eligibilityStatus = null;
            
            // Check against policy thresholds
            if (cgpa.compareTo(BigDecimal.valueOf(4.60)) >= 0) {
                eligibilityType = "Fast-Track";
                eligibilityStatus = "Immediate Review";
            } else if (cgpa.compareTo(BigDecimal.valueOf(4.20)) >= 0) {
                eligibilityType = "Vertical";
                eligibilityStatus = "Eligible";
            } else if (cgpa.compareTo(BigDecimal.valueOf(3.50)) >= 0) {
                eligibilityType = "Horizontal";
                eligibilityStatus = "Eligible";
            }
            
            if (eligibilityType != null) {
                Map<String, Object> emp = toEmployeeDto(employee);
                emp.put("cgpa", cgpa);
                emp.put("promotionType", eligibilityType);
                emp.put("status", eligibilityStatus);
                
                // Calculate target role
                Integer currentLevel = employee.getJobLevel();
                if (currentLevel == null) currentLevel = 1;
                
                JobLevel currentJobLevel = jobLevelRepository.findByLevelNumber(currentLevel).orElse(null);
                JobLevel targetJobLevel = jobLevelRepository.findByLevelNumber(
                    eligibilityType.equals("Vertical") ? currentLevel + 1 : currentLevel
                ).orElse(null);
                
                emp.put("currentLevel", currentLevel);
                emp.put("currentTitle", currentJobLevel != null ? currentJobLevel.getTitle() : employee.getJobTitle());
                emp.put("targetLevel", eligibilityType.equals("Vertical") ? currentLevel + 1 : currentLevel);
                emp.put("targetTitle", targetJobLevel != null ? targetJobLevel.getTitle() : "TBD");
                
                eligible.add(emp);
            }
        }
        
        // Sort by CGPA descending
        eligible.sort((a, b) -> ((BigDecimal)b.get("cgpa")).compareTo((BigDecimal)a.get("cgpa")));
        
        return eligible;
    }

    // =====================================================
    // CERTIFICATES / TRAINING
    // =====================================================

    /**
     * Get all training records with employee info.
     */
    public List<Map<String, Object>> getAllTrainingRecords() {
        // This would use TrainingRecordRepository
        // For now, return empty - integrate with existing training service
        return new ArrayList<>();
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
        dto.put("hireDate", profile.getHireDate());
        dto.put("yearsOfExperience", profile.getYearsOfExperience());
        dto.put("status", profile.getStatus());
        dto.put("employeeId", profile.getEmployeeId());
        dto.put("gender", profile.getGender());
        dto.put("teamLeadRequestStatus", profile.getTeamLeadRequestStatus());
        dto.put("teamLeadRequestedAt", profile.getTeamLeadRequestedAt());
        
        // Generate gender-based avatar URL
        String seed = profile.getEmployeeId() != null ? profile.getEmployeeId() : 
                      (profile.getEmail() != null ? profile.getEmail() : profile.getId().toString());
        String gender = profile.getGender();
        String avatarUrl;
        if ("male".equalsIgnoreCase(gender)) {
            avatarUrl = "https://api.dicebear.com/7.x/adventurer/svg?seed=" + seed + "&gender=male";
        } else if ("female".equalsIgnoreCase(gender)) {
            avatarUrl = "https://api.dicebear.com/7.x/adventurer/svg?seed=" + seed + "&gender=female";
        } else {
            avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=" + seed;
        }
        dto.put("avatar", avatarUrl);
        
        return dto;
    }

    private Map<String, Object> toTeamDto(Team team, boolean managed) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", team.getId());
        dto.put("name", team.getName());
        dto.put("description", team.getDescription() != null ? team.getDescription() : "");
        dto.put("isActive", team.getIsActive());
        dto.put("memberCount", profileRepository.countByDepartment(team.getName()));
        dto.put("managed", managed);
        dto.put("createdAt", team.getCreatedAt());
        return dto;
    }

    private Map<String, Object> toProbationDto(ProbationRecord record) {
        Profile employee = profileRepository.findById(record.getEmployeeId()).orElse(null);
        
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", record.getId());
        dto.put("employeeId", record.getEmployeeId());
        dto.put("employeeName", employee != null ? employee.getFullName() : "Unknown");
        dto.put("employeeRole", employee != null ? employee.getJobTitle() : null);
        dto.put("employeeEmail", employee != null ? employee.getEmail() : null);
        dto.put("startDate", record.getStartDate());
        dto.put("endDate", record.getCurrentEndDate());
        dto.put("appraisalDate", record.getAppraisalScheduledDate());
        dto.put("score", record.getAppraisalScore());
        dto.put("status", record.getStatus());
        dto.put("extensionCount", record.getExtensionCount());
        dto.put("recommendation", record.getRecommendation());
        dto.put("policyRecommendation", record.getPolicyRecommendation());
        dto.put("performanceBand", record.getPerformanceBand());
        dto.put("daysRemaining", record.getDaysRemaining());
        dto.put("isOverdue", record.isOverdue());
        dto.put("isInGracePeriod", record.isInGracePeriod());
        dto.put("isDueForConfirmation", !record.getCurrentEndDate().isAfter(LocalDate.now()));
        
        return dto;
    }

    private Map<String, Object> toPipDto(PipRecord record) {
        Profile employee = profileRepository.findById(record.getEmployeeId()).orElse(null);
        
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", record.getId());
        dto.put("employeeId", record.getEmployeeId());
        dto.put("employeeName", employee != null ? employee.getFullName() : "Unknown");
        dto.put("employeeRole", employee != null ? employee.getJobTitle() : null);
        dto.put("startDate", record.getStartDate());
        dto.put("endDate", record.getEndDate());
        dto.put("status", record.getStatus());
        dto.put("triggerReason", record.getTriggerReason());
        dto.put("triggerScore", record.getTriggerScore());
        dto.put("placedOn", record.getStartDate());
        dto.put("daysRemaining", record.getDaysRemaining());
        dto.put("weeksRemaining", record.getWeeksRemaining());
        dto.put("progressPercentage", record.getProgressPercentage());
        dto.put("isOverdue", record.isOverdue());
        dto.put("overdueDays", record.isOverdue()
            ? java.time.temporal.ChronoUnit.DAYS.between(record.getEndDate(), LocalDate.now())
            : 0);
        dto.put("goals", record.getGoals().stream().map(g -> Map.of(
            "id", g.getId(),
            "description", g.getGoalDescription(),
            "status", g.getStatus(),
            "progress", g.getProgressPercentage()
        )).collect(Collectors.toList()));
        
        return dto;
    }

    @Autowired
    private com.schoolable.backend.performance.EnhancedAuraService enhancedAuraService;

    private Double getEmployeeAuraScore(UUID employeeId) {
        try {
            var dashboard = enhancedAuraService.getEnhancedAuraDashboard(employeeId);
            Double score = dashboard.getAuraScore(); // out of 100
            if (score == null) return null;
            return score / 20.0; // Convert to 0-5 scale
        } catch (Exception e) {
            return null;
        }
    }
    @Transactional
    public void seedHRData() {
        List<Profile> all = profileRepository.findAll();
        if (all.isEmpty()) return;
        
        Collections.shuffle(all);
        UUID adminId = all.get(0).getId(); // Use first as admin
        
        // Seed Probations
        if (probationRepository.count() == 0) {
            int count = Math.min(all.size(), 3);
            for (int i = 0; i < count; i++) {
                createProbation(all.get(i).getId(), LocalDate.now().minusMonths(1), 3, adminId, adminId);
            }
        }
        
        // Seed PIPs
        if (pipRepository.count() == 0) {
            int start = Math.min(all.size(), 3);
            int count = Math.min(all.size() - start, 2);
            for (int i = 0; i < count; i++) {
                createPip(all.get(start + i).getId(), "Performance below 50%", new BigDecimal("45.0"), "Q1", 2024, adminId, adminId);
            }
        }
        
        // Seed Team Leads
        if (teamLeadRepository.count() == 0) {
            int start = Math.min(all.size(), 5);
            if (all.size() > start) {
                appointTeamLead(all.get(start).getId(), "Engineering Team", adminId);
            }
        }
    }
}
