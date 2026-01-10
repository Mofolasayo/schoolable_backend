package com.schoolable.backend.performance;

import com.schoolable.backend.attendance.AttendanceRepository;
import com.schoolable.backend.compliance.ComplianceSubmissionRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.schoolable.backend.performance.AuraPillarConfig.*;

/**
 * Service for auto-calculating sub-metric scores.
 * Runs on a schedule to update scores based on system data.
 */
@Service
public class SubMetricCalculationService {

    private static final Logger log = LoggerFactory.getLogger(SubMetricCalculationService.class);

    @Autowired
    private SubMetricScoreRepository subMetricRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TrainingRecordRepository trainingRecordRepository;

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @Autowired
    private PeerFeedbackRepository peerFeedbackRepository;

    @Autowired
    private ComplianceSubmissionRepository complianceSubmissionRepository;

    /**
     * Scheduled job: Calculate all auto-metrics every Sunday at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    @Transactional
    public void scheduledAutoCalculation() {
        log.info("Starting scheduled sub-metric calculation at {}", OffsetDateTime.now());
        calculateAllEmployeeMetrics();
    }

    /**
     * Calculate all sub-metrics for all employees
     */
    @Transactional
    public void calculateAllEmployeeMetrics() {
        List<Profile> employees = profileRepository.findByRoleNot("admin");
        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        for (Profile employee : employees) {
            calculateEmployeeMetrics(employee.getId(), quarter, year);
        }
    }

    /**
     * Calculate all sub-metrics for a specific employee
     */
    @Transactional
    public void calculateEmployeeMetrics(UUID employeeId, String quarter, int year) {
        Profile profile = profileRepository.findById(employeeId).orElse(null);
        if (profile == null) return;

        boolean isLeadership = isLeadershipRole(profile);
        LocalDate quarterStart = getQuarterStartDate(quarter, year);
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);

        // Calculate Technical Pillar metrics
        if (isLeadership) {
            calculateTechnicalLeadershipMetrics(employeeId, quarter, year, quarterStartOdt);
        } else {
            calculateTechnicalOperationalMetrics(employeeId, quarter, year, quarterStartOdt);
        }

        // Calculate Behavioral Pillar metrics
        calculateBehavioralMetrics(employeeId, quarter, year, quarterStartOdt);

        // Calculate Culture Fit Pillar metrics
        calculateCultureFitMetrics(employeeId, quarter, year, quarterStartOdt);

        // Calculate Growth & Learning Pillar metrics
        calculateGrowthMetrics(employeeId, quarter, year, quarterStart);

        // Calculate Leadership Pillar metrics (Team Leads only)
        if (isTeamLead(profile)) {
            calculateLeadershipMetrics(employeeId, quarter, year);
        }
    }

    // ==================== TECHNICAL - OPERATIONAL ====================

    private void calculateTechnicalOperationalMetrics(UUID employeeId, String quarter, int year, OffsetDateTime quarterStart) {
        // 1. Process Execution Accuracy: Tasks completed without reopening/revision
        long totalCompleted = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStart);
        // For now, assume all completed tasks are executed correctly (can be enhanced with revision tracking)
        double processScore = totalCompleted > 0 ? 85.0 : 50.0;
        saveSubMetricScore(employeeId, PILLAR_TECHNICAL, TECH_OP_PROCESS_EXECUTION, processScore, SOURCE_AUTO, quarter, year);

        // 2. Documentation Quality: Tasks with attachments
        long tasksWithDocs = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStart);
        // Placeholder - would need attachment tracking
        double docScore = 70.0;
        saveSubMetricScore(employeeId, PILLAR_TECHNICAL, TECH_OP_DOCUMENTATION, docScore, SOURCE_AUTO, quarter, year);

        // 3. Task Completion Timeliness: On-time completion rate
        long totalTasks = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStart);
        long completedTasks = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStart);
        double timelinessScore = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 50.0;
        saveSubMetricScore(employeeId, PILLAR_TECHNICAL, TECH_OP_TASK_TIMELINESS, timelinessScore, SOURCE_AUTO, quarter, year);

        // 4. SOP Compliance: From compliance module
        double complianceScore = calculateComplianceScore(employeeId);
        saveSubMetricScore(employeeId, PILLAR_TECHNICAL, TECH_OP_SOP_COMPLIANCE, complianceScore, SOURCE_AUTO, quarter, year);

        // 5. Problem-Solving: Team Lead rating (get from weekly reports)
        double problemSolvingScore = getTeamLeadRating(employeeId, quarter, year, "technical_score") * 20;
        saveSubMetricScore(employeeId, PILLAR_TECHNICAL, TECH_OP_PROBLEM_SOLVING, problemSolvingScore, SOURCE_TEAM_LEAD, quarter, year);
    }

    // ==================== TECHNICAL - LEADERSHIP ====================

    private void calculateTechnicalLeadershipMetrics(UUID employeeId, String quarter, int year, OffsetDateTime quarterStart) {
        // Leadership technical metrics require admin ratings (default to 50 if not rated)
        double defaultScore = 50.0;

        // These would be set by admin through a separate interface
        // For now, check if existing scores exist, otherwise use default
        saveSubMetricScoreIfMissing(employeeId, PILLAR_TECHNICAL, TECH_LEAD_STRATEGIC_VISION, defaultScore, SOURCE_ADMIN, quarter, year);
        saveSubMetricScoreIfMissing(employeeId, PILLAR_TECHNICAL, TECH_LEAD_BUSINESS_IMPACT, defaultScore, SOURCE_ADMIN, quarter, year);
        saveSubMetricScoreIfMissing(employeeId, PILLAR_TECHNICAL, TECH_LEAD_RESOURCE_ALLOCATION, defaultScore, SOURCE_ADMIN, quarter, year);
        saveSubMetricScoreIfMissing(employeeId, PILLAR_TECHNICAL, TECH_LEAD_DECISION_QUALITY, defaultScore, SOURCE_ADMIN, quarter, year);
        saveSubMetricScoreIfMissing(employeeId, PILLAR_TECHNICAL, TECH_LEAD_RISK_MANAGEMENT, defaultScore, SOURCE_ADMIN, quarter, year);
    }

    // ==================== BEHAVIORAL ====================

    private void calculateBehavioralMetrics(UUID employeeId, String quarter, int year, OffsetDateTime quarterStart) {
        // 1. Teamwork & Collaboration: Peer feedback + message activity
        double teamworkScore = getPeerFeedbackAverage(employeeId, quarter, year, "support_rating");
        if (teamworkScore == 0) teamworkScore = 70.0; // Default if no feedback
        saveSubMetricScore(employeeId, PILLAR_BEHAVIORAL, BEHAV_TEAMWORK, teamworkScore, SOURCE_AUTO, quarter, year);

        // 2. Professionalism: Attendance consistency
        long totalAttendance = attendanceRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStart);
        long presentCount = attendanceRepository.countByUserIdAndStatusAndCreatedAtAfter(employeeId, "present", quarterStart);
        double professionalismScore = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 70.0;
        saveSubMetricScore(employeeId, PILLAR_BEHAVIORAL, BEHAV_PROFESSIONALISM, professionalismScore, SOURCE_AUTO, quarter, year);

        // 3. Time Management: Task completion rate
        long totalTasks = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStart);
        long completedTasks = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStart);
        double timeManagementScore = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 70.0;
        saveSubMetricScore(employeeId, PILLAR_BEHAVIORAL, BEHAV_TIME_MANAGEMENT, timeManagementScore, SOURCE_AUTO, quarter, year);

        // 4. Adaptability: Peer feedback or Team Lead rating
        double adaptabilityScore = getTeamLeadRating(employeeId, quarter, year, "adaptability_score") * 20;
        if (adaptabilityScore == 0) adaptabilityScore = 50.0;
        saveSubMetricScore(employeeId, PILLAR_BEHAVIORAL, BEHAV_ADAPTABILITY, adaptabilityScore, SOURCE_PEER_FEEDBACK, quarter, year);

        // 5. Initiative: Team Lead rating
        double initiativeScore = getTeamLeadRating(employeeId, quarter, year, "initiative_score") * 20;
        if (initiativeScore == 0) initiativeScore = 50.0;
        saveSubMetricScore(employeeId, PILLAR_BEHAVIORAL, BEHAV_INITIATIVE, initiativeScore, SOURCE_TEAM_LEAD, quarter, year);
    }

    // ==================== CULTURE FIT ====================

    private void calculateCultureFitMetrics(UUID employeeId, String quarter, int year, OffsetDateTime quarterStart) {
        // 1. Adherence to Company Values: Peer feedback
        double valuesScore = getPeerFeedbackAverage(employeeId, quarter, year, "values_rating");
        if (valuesScore == 0) valuesScore = 70.0;
        saveSubMetricScore(employeeId, PILLAR_CULTURE_FIT, CULTURE_COMPANY_VALUES, valuesScore, SOURCE_PEER_FEEDBACK, quarter, year);

        // 2. Work Ethics & Integrity: Attendance + Team Lead rating
        long totalAttendance = attendanceRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStart);
        long presentCount = attendanceRepository.countByUserIdAndStatusAndCreatedAtAfter(employeeId, "present", quarterStart);
        double attendanceRate = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 70.0;
        double integrityTL = getTeamLeadRating(employeeId, quarter, year, "integrity_score") * 20;
        double workEthicsScore = integrityTL > 0 ? (attendanceRate * 0.5 + integrityTL * 0.5) : attendanceRate;
        saveSubMetricScore(employeeId, PILLAR_CULTURE_FIT, CULTURE_WORK_ETHICS, workEthicsScore, SOURCE_AUTO, quarter, year);

        // 3. Accountability & Ownership: Peer feedback + task ownership
        double accountabilityScore = getPeerFeedbackAverage(employeeId, quarter, year, "accountability_rating");
        if (accountabilityScore == 0) accountabilityScore = 70.0;
        saveSubMetricScore(employeeId, PILLAR_CULTURE_FIT, CULTURE_ACCOUNTABILITY, accountabilityScore, SOURCE_PEER_FEEDBACK, quarter, year);

        // 4. Attitude Towards Work & Colleagues: Team Lead rating
        double attitudeScore = getTeamLeadRating(employeeId, quarter, year, "attitude_towards_work_score") * 20;
        if (attitudeScore == 0) attitudeScore = 50.0;
        saveSubMetricScore(employeeId, PILLAR_CULTURE_FIT, CULTURE_ATTITUDE, attitudeScore, SOURCE_TEAM_LEAD, quarter, year);

        // 5. Respect for Organizational Norms & Policies: Compliance + Attendance
        double complianceScore = calculateComplianceScore(employeeId);
        double respectScore = (complianceScore + attendanceRate) / 2;
        saveSubMetricScore(employeeId, PILLAR_CULTURE_FIT, CULTURE_RESPECT_POLICIES, respectScore, SOURCE_AUTO, quarter, year);
    }

    // ==================== GROWTH & LEARNING ====================

    private void calculateGrowthMetrics(UUID employeeId, String quarter, int year, LocalDate quarterStart) {
        // 1. Skill Development: Training records completed + certificates
        long completedTrainings = trainingRecordRepository.countCompletedTrainingsInPeriod(employeeId, quarterStart);
        double skillDevScore;
        if (completedTrainings >= 5) skillDevScore = 100.0;
        else if (completedTrainings >= 3) skillDevScore = 80.0;
        else if (completedTrainings >= 1) skillDevScore = 60.0;
        else skillDevScore = 40.0;
        saveSubMetricScore(employeeId, PILLAR_GROWTH, GROWTH_SKILL_DEV, skillDevScore, SOURCE_AUTO, quarter, year);

        // 2. Participation in Training: Count of trainings attended
        double participationScore = skillDevScore; // Same logic for now
        saveSubMetricScore(employeeId, PILLAR_GROWTH, GROWTH_TRAINING_PARTICIPATION, participationScore, SOURCE_AUTO, quarter, year);

        // 3. Application of New Skills: Team Lead rating
        double applySkillsScore = getTeamLeadRating(employeeId, quarter, year, "self_initiative_score") * 20;
        if (applySkillsScore == 0) applySkillsScore = 50.0;
        saveSubMetricScore(employeeId, PILLAR_GROWTH, GROWTH_APPLY_SKILLS, applySkillsScore, SOURCE_TEAM_LEAD, quarter, year);

        // 4. Continuous Improvement: Aura score trend (compare to previous quarter)
        double improvementScore = calculateImprovementTrend(employeeId, quarter, year);
        saveSubMetricScore(employeeId, PILLAR_GROWTH, GROWTH_CONTINUOUS_IMPROVEMENT, improvementScore, SOURCE_AUTO, quarter, year);

        // 5. Openness to Feedback: Peer feedback
        double feedbackScore = getPeerFeedbackAverage(employeeId, quarter, year, "feedback_rating");
        if (feedbackScore == 0) feedbackScore = 70.0;
        saveSubMetricScore(employeeId, PILLAR_GROWTH, GROWTH_OPENNESS_FEEDBACK, feedbackScore, SOURCE_PEER_FEEDBACK, quarter, year);
    }

    // ==================== LEADERSHIP (Team Leads Only) ====================

    private void calculateLeadershipMetrics(UUID employeeId, String quarter, int year) {
        // Leadership metrics come from team feedback (subordinates rating their TL) and admin ratings
        double defaultScore = 50.0;

        // 1. Organizational Guidance: Team feedback
        double orgGuidanceScore = getTeamFeedbackAverage(employeeId, quarter, year, "org_guidance_rating");
        if (orgGuidanceScore == 0) orgGuidanceScore = defaultScore;
        saveSubMetricScore(employeeId, PILLAR_LEADERSHIP, LEAD_ORG_GUIDANCE, orgGuidanceScore, SOURCE_TEAM_FEEDBACK, quarter, year);

        // 2. People & Culture Leadership: Team feedback
        double peopleCultureScore = getTeamFeedbackAverage(employeeId, quarter, year, "people_culture_rating");
        if (peopleCultureScore == 0) peopleCultureScore = defaultScore;
        saveSubMetricScore(employeeId, PILLAR_LEADERSHIP, LEAD_PEOPLE_CULTURE, peopleCultureScore, SOURCE_TEAM_FEEDBACK, quarter, year);

        // 3. Executive Decision-Making: Admin rating
        saveSubMetricScoreIfMissing(employeeId, PILLAR_LEADERSHIP, LEAD_EXEC_DECISION, defaultScore, SOURCE_ADMIN, quarter, year);

        // 4. Crisis/Conflict Handling: Admin rating
        saveSubMetricScoreIfMissing(employeeId, PILLAR_LEADERSHIP, LEAD_CRISIS_HANDLING, defaultScore, SOURCE_ADMIN, quarter, year);

        // 5. Leadership Influence: Team feedback
        double influenceScore = getTeamFeedbackAverage(employeeId, quarter, year, "influence_rating");
        if (influenceScore == 0) influenceScore = defaultScore;
        saveSubMetricScore(employeeId, PILLAR_LEADERSHIP, LEAD_INFLUENCE, influenceScore, SOURCE_TEAM_FEEDBACK, quarter, year);
    }

    // ==================== HELPER METHODS ====================

    private void saveSubMetricScore(UUID employeeId, String pillar, String subMetric, 
                                     Double score, String source, String quarter, int year) {
        Optional<SubMetricScore> existing = subMetricRepository
            .findByEmployeeIdAndPillarAndSubMetricAndQuarterAndYear(employeeId, pillar, subMetric, quarter, year);

        SubMetricScore sms;
        if (existing.isPresent()) {
            sms = existing.get();
            sms.setScore(Math.min(100.0, Math.max(0.0, score)));
            sms.setCalculatedAt(OffsetDateTime.now());
        } else {
            sms = new SubMetricScore(employeeId, pillar, subMetric, 
                Math.min(100.0, Math.max(0.0, score)), source, quarter, year);
        }
        subMetricRepository.save(sms);
    }

    private void saveSubMetricScoreIfMissing(UUID employeeId, String pillar, String subMetric, 
                                              Double score, String source, String quarter, int year) {
        Optional<SubMetricScore> existing = subMetricRepository
            .findByEmployeeIdAndPillarAndSubMetricAndQuarterAndYear(employeeId, pillar, subMetric, quarter, year);

        if (existing.isEmpty()) {
            SubMetricScore sms = new SubMetricScore(employeeId, pillar, subMetric, score, source, quarter, year);
            subMetricRepository.save(sms);
        }
    }

    private double getTeamLeadRating(UUID employeeId, String quarter, int year, String field) {
        LocalDate quarterStart = getQuarterStartDate(quarter, year);
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);

        if (reports.isEmpty()) return 0;

        return reports.stream()
            .map(r -> {
                switch (field) {
                    case "technical_score": return r.getTechnicalScore();
                    case "initiative_score": return r.getInitiativeScore();
                    case "adaptability_score": return r.getAdaptabilityScore();
                    case "integrity_score": return r.getIntegrityScore();
                    case "attitude_towards_work_score": return r.getAttitudeTowardsWorkScore();
                    case "self_initiative_score": return r.getSelfInitiativeScore();
                    default: return null;
                }
            })
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0) * 20; // Convert 1-5 to 0-100
    }

    private double getPeerFeedbackAverage(UUID employeeId, String quarter, int year, String field) {
        List<PeerFeedback> feedback = peerFeedbackRepository
            .findByToEmployeeIdAndQuarterAndYear(employeeId, quarter, year);

        if (feedback.isEmpty()) return 0;

        // Extract the appropriate rating based on field name
        return feedback.stream()
            .map(f -> {
                switch (field) {
                    case "support_rating": return f.getSupportRating();
                    case "collaboration_rating": return f.getCollaborationRating();
                    case "communication_rating": return f.getCommunicationRating();
                    case "adaptability_rating": return f.getAdaptabilityRating();
                    case "values_rating": return f.getValuesRating();
                    case "accountability_rating": return f.getAccountabilityRating();
                    case "feedback_rating": return f.getFeedbackRating();
                    case "org_guidance_rating": return f.getOrgGuidanceRating();
                    case "people_culture_rating": return f.getPeopleCultureRating();
                    case "influence_rating": return f.getInfluenceRating();
                    default: return f.getSupportRating();
                }
            })
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0) * 20; // Convert 1-5 to 0-100
    }

    private double getTeamFeedbackAverage(UUID employeeId, String quarter, int year, String field) {
        // Team feedback uses the same peer_feedback table but from subordinates rating their TL
        // The "to_employee_id" is the team lead being rated
        // We use the leadership-specific fields (org_guidance_rating, etc.)
        List<PeerFeedback> feedback = peerFeedbackRepository
            .findByToEmployeeIdAndQuarterAndYear(employeeId, quarter, year);

        if (feedback.isEmpty()) return 0;

        return feedback.stream()
            .map(f -> {
                switch (field) {
                    case "org_guidance_rating": return f.getOrgGuidanceRating();
                    case "people_culture_rating": return f.getPeopleCultureRating();
                    case "influence_rating": return f.getInfluenceRating();
                    default: return null;
                }
            })
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0) * 20; // Convert 1-5 to 0-100
    }

    private double calculateComplianceScore(UUID employeeId) {
        try {
            long compliant = complianceSubmissionRepository.countByUserIdAndStatus(employeeId, "approved") +
                           complianceSubmissionRepository.countByUserIdAndStatus(employeeId, "submitted");
            long total = complianceSubmissionRepository.countByUserId(employeeId);

            if (total == 0) return 100.0; // No compliance requirements = fully compliant
            return (compliant * 100.0) / total;
        } catch (Exception e) {
            return 100.0; // Default to compliant if module not available
        }
    }

    private double calculateImprovementTrend(UUID employeeId, String quarter, int year) {
        // Compare current quarter scores to previous quarter
        String prevQuarter = getPreviousQuarter(quarter);
        int prevYear = "Q1".equals(quarter) ? year - 1 : year;

        List<SubMetricScore> currentScores = subMetricRepository
            .findByEmployeeIdAndQuarterAndYear(employeeId, quarter, year);
        List<SubMetricScore> prevScores = subMetricRepository
            .findByEmployeeIdAndQuarterAndYear(employeeId, prevQuarter, prevYear);

        if (prevScores.isEmpty()) return 70.0; // No previous data, neutral score

        double currentAvg = currentScores.stream().mapToDouble(SubMetricScore::getScore).average().orElse(50);
        double prevAvg = prevScores.stream().mapToDouble(SubMetricScore::getScore).average().orElse(50);

        double improvement = currentAvg - prevAvg;
        // Convert improvement to 0-100 scale
        // +10 points improvement = 100, -10 = 0
        return Math.min(100, Math.max(0, 50 + (improvement * 5)));
    }

    private boolean isLeadershipRole(Profile profile) {
        if (profile == null) return false;
        String role = profile.getRole();
        String jobTitle = profile.getJobTitle();

        return "team_lead".equalsIgnoreCase(role) ||
               "manager".equalsIgnoreCase(role) ||
               (jobTitle != null && (
                   jobTitle.toLowerCase().contains("lead") ||
                   jobTitle.toLowerCase().contains("manager") ||
                   jobTitle.toLowerCase().contains("director") ||
                   jobTitle.toLowerCase().contains("head")
               ));
    }

    private boolean isTeamLead(Profile profile) {
        return profile != null && "team_lead".equalsIgnoreCase(profile.getRole());
    }

    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    private String getPreviousQuarter(String quarter) {
        switch (quarter) {
            case "Q1": return "Q4";
            case "Q2": return "Q1";
            case "Q3": return "Q2";
            case "Q4": return "Q3";
            default: return "Q4";
        }
    }

    private LocalDate getQuarterStartDate(String quarter, int year) {
        switch (quarter) {
            case "Q1": return LocalDate.of(year, 1, 1);
            case "Q2": return LocalDate.of(year, 4, 1);
            case "Q3": return LocalDate.of(year, 7, 1);
            case "Q4": return LocalDate.of(year, 10, 1);
            default: return LocalDate.of(year, 1, 1);
        }
    }
}
