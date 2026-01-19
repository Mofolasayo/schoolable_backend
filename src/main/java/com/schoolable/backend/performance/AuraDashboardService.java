package com.schoolable.backend.performance;

import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.task.TaskRepository;
import com.schoolable.backend.attendance.AttendanceRepository;
import com.schoolable.backend.announcement.AnnouncementReadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service for calculating and retrieving Aura scores for employees.
 * Combines auto-calculated metrics with team lead weekly ratings.
 * 
 * V2: Updated with Training, Peer Feedback, and more auto-calculations.
 */
@Service
public class AuraDashboardService {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TrainingRecordRepository trainingRecordRepository;

    @Autowired
    private PeerFeedbackRepository peerFeedbackRepository;

    @Autowired
    private AnnouncementReadRepository announcementReadRepository;

    @Autowired
    private AutoAuraCalculationService autoAuraService;

    /**
     * Get the full Aura dashboard data for an employee.
     * This is the main endpoint for the mobile app.
     */
    public AuraDashboardDto.EmployeeAuraResponse getEmployeeAuraDashboard(UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Map<String, Object> autoAura = autoAuraService.calculateEmployeeScore(profile);
        if (autoAura.containsKey("error")) {
            throw new RuntimeException(autoAura.get("error").toString());
        }

        AuraDashboardDto.EmployeeAuraResponse response = new AuraDashboardDto.EmployeeAuraResponse();
        response.setEmployeeId(employeeId.toString());
        response.setEmployeeName(profile.getFullName());
        response.setDepartment(profile.getDepartment());
        response.setRole(profile.getRole());

        String quarter = stringValue(autoAura.get("quarter"));
        Integer year = intValue(autoAura.get("year"));
        if (quarter != null) {
            response.setCurrentQuarter(quarter);
        }
        if (year != null) {
            response.setCurrentYear(year);
        }

        Double auraScore = doubleValue(autoAura.get("auraScore"));
        Double qgpa = doubleValue(autoAura.get("qgpa"));
        response.setAuraScore(auraScore != null ? Math.round(auraScore * 100.0) / 100.0 : null);
        response.setQgpa(qgpa != null ? Math.round(qgpa * 100.0) / 100.0 : null);
        response.setGrade(stringValue(autoAura.get("grade")));

        AuraDashboardDto.PillarScores pillars = buildPillarsFromAuto(profile, autoAura);
        response.setPillars(pillars);

        LocalDate now = LocalDate.now();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;
        int weeksRated = getWeeksRatedThisQuarter(employeeId, currentQuarter, now.getYear());
        response.setWeeksRatedThisQuarter(weeksRated);

        return response;
    }

    /**
     * Calculate Technical Competence pillar (25%)
     * Now primarily set by Team Lead (each team has different criteria)
     * Falls back to task completion if no TL rating
     */
    private AuraDashboardDto.PillarDetail calculateTechnicalPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Technical Competence");
        pillar.setWeight(25.0);
        pillar.setDataSource("team_lead");

        LocalDate quarterStart = getQuarterStartDate();
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);

        double technicalScore = 50.0; // Default

        if (!reports.isEmpty()) {
            // Use Team Lead's technical score rating (1-5) × 20 = 0-100
            technicalScore = reports.stream()
                .filter(r -> r.getTechnicalScore() != null)
                .mapToInt(WeeklyPerformanceReport::getTechnicalScore)
                .average()
                .orElse(2.5) * 20;
        } else {
            // Fallback: calculate from task completion if no TL rating
            OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
            long totalTasks = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStartOdt);
            long completedTasks = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStartOdt);
            technicalScore = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 50.0;
            pillar.setDataSource("auto"); // Mark as auto if using fallback
        }
        
        pillar.setScore(Math.round(technicalScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(technicalScore * 0.25 * 100.0) / 100.0);

        return pillar;
    }

    /**
     * Calculate Behavioral Competence pillar (25%)
     * Components (all 20% each):
     * - Teamwork & Collaboration - Team Lead rating
     * - Initiative - Team Lead rating
     * - Professionalism - Auto (attendance)
     * - Time Management - Auto (task deadlines)
     * - Adaptability - Team Lead rating (NEW)
     */
    private AuraDashboardDto.PillarDetail calculateBehavioralPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Behavioral Competence");
        pillar.setWeight(25.0);
        pillar.setDataSource("mixed");

        // Get Team Lead ratings for this quarter
        LocalDate quarterStart = getQuarterStartDate();
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);

        double teamworkScore = 50.0;
        double initiativeScore = 50.0;
        double adaptabilityScore = 50.0;

        if (!reports.isEmpty()) {
            // Teamwork & Collaboration (Team Lead 1-5 → 0-100)
            teamworkScore = reports.stream()
                .filter(r -> r.getTeamworkCollaborationScore() != null)
                .mapToInt(WeeklyPerformanceReport::getTeamworkCollaborationScore)
                .average()
                .orElse(2.5) * 20;

            // Initiative (Team Lead 1-5 → 0-100)
            initiativeScore = reports.stream()
                .filter(r -> r.getInitiativeScore() != null)
                .mapToInt(WeeklyPerformanceReport::getInitiativeScore)
                .average()
                .orElse(2.5) * 20;

            // Adaptability (NEW - Team Lead 1-5 → 0-100)
            adaptabilityScore = reports.stream()
                .filter(r -> r.getAdaptabilityScore() != null)
                .mapToInt(WeeklyPerformanceReport::getAdaptabilityScore)
                .average()
                .orElse(2.5) * 20;
        }

        // Professionalism from attendance (Auto)
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        long totalAttendance = attendanceRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long presentCount = attendanceRepository.countByUserIdAndStatusAndCreatedAtAfter(employeeId, "present", quarterStartOdt);
        double professionalismScore = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 70.0;

        // Time management from task deadline performance (Auto)
        long totalTasks = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long completedTasks = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStartOdt);
        double timeManagementScore = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 70.0;

        // Calculate weighted average (20% each)
        double behavioralScore = (
            teamworkScore * 0.2 +
            initiativeScore * 0.2 +
            professionalismScore * 0.2 +
            timeManagementScore * 0.2 +
            adaptabilityScore * 0.2
        );

        pillar.setScore(Math.round(behavioralScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(behavioralScore * 0.25 * 100.0) / 100.0);

        return pillar;
    }

    /**
     * Calculate Culture Fit pillar (25%)
     * Components (all 20% each):
     * - Attitude Towards Work - Team Lead rating
     * - Work Ethics - Auto (punctuality + attendance consistency)
     * - Integrity - Team Lead rating (NEW)
     * - Communication & Engagement - Auto (announcement reads)
     * - Policy Compliance - Auto (attendance)
     */
    private AuraDashboardDto.PillarDetail calculateCultureFitPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Culture Fit");
        pillar.setWeight(25.0);
        pillar.setDataSource("mixed");

        LocalDate quarterStart = getQuarterStartDate();
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);

        // 1. Attitude from Team Lead (20% of pillar)
        double attitudeScore = 50.0;
        if (!reports.isEmpty()) {
            attitudeScore = reports.stream()
                .filter(r -> r.getAttitudeTowardsWorkScore() != null)
                .mapToInt(WeeklyPerformanceReport::getAttitudeTowardsWorkScore)
                .average()
                .orElse(2.5) * 20;
        }

        // 2. Work Ethics (20% of pillar - Auto: punctuality + attendance consistency)
        // Calculated from: on-time arrivals + consistent presence
        long totalAttendance = attendanceRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long presentCount = attendanceRepository.countByUserIdAndStatusAndCreatedAtAfter(employeeId, "present", quarterStartOdt);
        double attendanceRate = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 70.0;
        // Work ethics = attendance consistency (no unexplained absences)
        double workEthicsScore = attendanceRate; 

        // 3. Integrity (20% of pillar - Team Lead rating)
        double integrityScore = 50.0;
        if (!reports.isEmpty()) {
            integrityScore = reports.stream()
                .filter(r -> r.getIntegrityScore() != null)
                .mapToInt(WeeklyPerformanceReport::getIntegrityScore)
                .average()
                .orElse(2.5) * 20;
        }

        // 4. Communication & Engagement (20% of pillar - Auto)
        double engagementScore = getAnnouncementEngagementScore(employeeId, quarterStartOdt);

        // 5. Policy Compliance (20% of pillar - Auto from attendance)
        // No violations = 100%, each violation reduces score
        double complianceScore = attendanceRate; // Same as work ethics for now

        // Calculate weighted average (20% each component)
        double cultureFitScore = (
            attitudeScore * 0.2 +
            workEthicsScore * 0.2 +
            integrityScore * 0.2 +
            engagementScore * 0.2 +
            complianceScore * 0.2
        );

        pillar.setScore(Math.round(cultureFitScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(cultureFitScore * 0.25 * 100.0) / 100.0);

        return pillar;
    }

    /**
     * Get peer feedback score for an employee (0-100)
     */
    private double getPeerFeedbackScore(UUID employeeId, OffsetDateTime quarterStart) {
        try {
            // Get peer feedback received this quarter
            String quarter = "Q" + ((LocalDate.now().getMonthValue() - 1) / 3 + 1);
            int year = LocalDate.now().getYear();
            
            List<PeerFeedback> feedback = peerFeedbackRepository
                .findByToEmployeeIdAndQuarterAndYear(employeeId, quarter, year);
            
            if (feedback.isEmpty()) {
                return 0.0; // No peer feedback
            }
            
            // Average peer ratings (1-5 scale) converted to 0-100
            double avgScore = feedback.stream()
                .mapToInt(PeerFeedback::getSupportRating)
                .average()
                .orElse(3.0) * 20;
            
            return avgScore;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get weekly rating trend for charts
     */
    public AuraDashboardDto.WeeklyRatingsHistory getWeeklyTrend(UUID employeeId, int limit) {
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdOrderByYearDescWeekNumberDesc(employeeId);

        AuraDashboardDto.WeeklyRatingsHistory history = new AuraDashboardDto.WeeklyRatingsHistory();
        history.setEmployeeId(employeeId.toString());
        
        List<AuraDashboardDto.WeeklyTrendPoint> weeks = new ArrayList<>();
        double totalAura = 0;

        int count = 0;
        for (WeeklyPerformanceReport report : reports) {
            if (count >= limit) break;
            
            AuraDashboardDto.WeeklyTrendPoint point = new AuraDashboardDto.WeeklyTrendPoint();
            point.setWeekNumber(report.getWeekNumber());
            point.setYear(report.getYear());
            point.setWeekStartDate(report.getWeekStartDate().toString());
            
            if (report.getTeamworkCollaborationScore() != null) {
                point.setTeamwork(report.getTeamworkCollaborationScore() * 20.0);
            }
            if (report.getInitiativeScore() != null) {
                point.setInitiative(report.getInitiativeScore() * 20.0);
            }
            if (report.getAttitudeTowardsWorkScore() != null) {
                point.setAttitude(report.getAttitudeTowardsWorkScore() * 20.0);
            }
            
            // Calculate simple average for this week's aura
            double weekAura = 0;
            int factors = 0;
            if (point.getTeamwork() != null) { weekAura += point.getTeamwork(); factors++; }
            if (point.getInitiative() != null) { weekAura += point.getInitiative(); factors++; }
            if (point.getAttitude() != null) { weekAura += point.getAttitude(); factors++; }
            if (factors > 0) {
                point.setAuraScore(weekAura / factors);
                totalAura += point.getAuraScore();
            }
            
            weeks.add(point);
            count++;
        }

        history.setWeeks(weeks);
        history.setTotalWeeksRated(weeks.size());
        history.setAverageAura(weeks.isEmpty() ? 0.0 : totalAura / weeks.size());

        return history;
    }

    /**
     * Calculate Collaboration pillar (25%)
     * V2 REVISED:
     * - Communication (10%) - AUTO from messages
     * - Cross-Functional Work (5%) - placeholder (needs channel analysis)
     * - Peer Support (5%) - AUTO from peer_feedback table
     * - Announcement Engagement (5%) - AUTO from announcement_reads
     */
    private AuraDashboardDto.PillarDetail calculateCollaborationPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Collaboration");
        pillar.setWeight(25.0);
        pillar.setDataSource("mixed");

        LocalDate quarterStart = getQuarterStartDate();
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);

        // Communication score (10%): Messaging disabled in production; use neutral baseline.
        double communicationScore = 60.0;

        // Cross-functional work (5%): (placeholder - would need channel_members analysis)
        double crossFunctionalScore = 60.0;

        // Peer Support (5%): From peer_feedback table
        double peerSupportScore = getPeerSupportScore(employeeId);

        // Announcement Engagement (5%): From announcement_reads table
        double engagementScore = getAnnouncementEngagementScore(employeeId, quarterStartOdt);

        // Calculate weighted average
        // Communication = 10/25 = 40%, CrossFunc = 5/25 = 20%, PeerSupport = 5/25 = 20%, Engagement = 5/25 = 20%
        double collaborationScore = (
            communicationScore * 0.4 +
            crossFunctionalScore * 0.2 +
            peerSupportScore * 0.2 +
            engagementScore * 0.2
        );

        pillar.setScore(Math.round(collaborationScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(collaborationScore * 0.25 * 100.0) / 100.0);


        return pillar;
    }

    /**
     * Calculate Growth & Learning pillar (25%)
     * Components:
     * - Training Completion (40%) - AUTO from training_records
     * - Self-Initiative (30%) - Team Lead rating (NEW)
     * - Knowledge Sharing (30%) - Announcements/docs created (placeholder)
     */
    private AuraDashboardDto.PillarDetail calculateGrowthPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Growth & Learning");
        pillar.setWeight(25.0);
        pillar.setDataSource("mixed");

        LocalDate quarterStart = getQuarterStartDate();
        
        // Get Team Lead ratings for this quarter
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);

        // 1. Training Completion (40%): Count completed trainings this quarter
        long completedTrainings = trainingRecordRepository.countCompletedTrainingsInPeriod(
            employeeId, quarterStart);
        
        double trainingScore;
        if (completedTrainings >= 5) {
            trainingScore = 100.0;
        } else if (completedTrainings >= 3) {
            trainingScore = 80.0;
        } else if (completedTrainings >= 1) {
            trainingScore = 60.0;
        } else {
            trainingScore = 40.0;
        }

        // 2. Self-Initiative (30%): Team Lead rating (NEW)
        double selfInitiativeScore = 50.0;
        if (!reports.isEmpty()) {
            selfInitiativeScore = reports.stream()
                .filter(r -> r.getSelfInitiativeScore() != null)
                .mapToInt(WeeklyPerformanceReport::getSelfInitiativeScore)
                .average()
                .orElse(2.5) * 20;
        }

        // 3. Knowledge Sharing (30%): Placeholder - would need announcements/attachments count
        double knowledgeSharingScore = 60.0;

        // Calculate weighted average
        double growthScore = (
            trainingScore * 0.4 +            // 40%
            selfInitiativeScore * 0.3 +      // 30%
            knowledgeSharingScore * 0.3      // 30%
        );

        pillar.setScore(Math.round(growthScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(growthScore * 0.25 * 100.0) / 100.0);

        return pillar;
    }

    /**
     * Get current quarter string (Q1, Q2, Q3, Q4)
     */
    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    /**
     * Get peer support score from peer feedback
     */
    private double getPeerSupportScore(UUID employeeId) {
        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();
        
        Double avgRating = peerFeedbackRepository.getOverallAverageRating(employeeId, quarter, year);
        
        if (avgRating == null) {
            return 60.0; // Default if no peer feedback
        }
        
        // Convert 1-5 rating to percentage
        return avgRating * 20;
    }

    /**
     * Get announcement engagement score
     * Based on percentage of announcements read this quarter
     */
    private double getAnnouncementEngagementScore(UUID employeeId, OffsetDateTime quarterStart) {
        long totalAnnouncements = announcementReadRepository.countTotalAnnouncementsAfter(quarterStart);
        
        if (totalAnnouncements == 0) {
            return 70.0; // Default if no announcements this quarter
        }
        
        long readAnnouncements = announcementReadRepository.countByUserIdAndReadAtAfter(employeeId, quarterStart);
        
        // Calculate engagement percentage
        double engagementRate = (double) readAnnouncements / totalAnnouncements * 100;
        
        // Score based on engagement rate
        if (engagementRate >= 90) {
            return 100.0;
        } else if (engagementRate >= 70) {
            return 85.0;
        } else if (engagementRate >= 50) {
            return 70.0;
        } else if (engagementRate >= 30) {
            return 55.0;
        } else {
            return 40.0;
        }
    }

    // Helper methods
    private LocalDate getQuarterStartDate() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
        return LocalDate.of(now.getYear(), quarterStartMonth, 1);
    }

    private int getWeeksRatedThisQuarter(UUID employeeId, int quarter, int year) {
        LocalDate quarterStart = getQuarterStartDate();
        return (int) weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart)
            .stream()
            .filter(r -> "submitted".equals(r.getStatus()))
            .count();
    }

    private String calculateGrade(double score) {
        if (score >= 86) return "A";
        if (score >= 76) return "B";
        if (score >= 66) return "C";
        if (score >= 50) return "D";
        return "F";
    }

    /**
     * Get all employees with their Aura scores and pillar breakdown.
     * Used by the dashboard to display the Employees tab.
     */
    public List<Map<String, Object>> getAllEmployeesWithAura() {
        List<Profile> allProfiles = profileRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Profile profile : allProfiles) {
            try {
                UUID employeeId = profile.getId();
                Map<String, Object> employeeData = new HashMap<>();
                Map<String, Object> autoAura = autoAuraService.calculateEmployeeScore(profile);
                if (autoAura.containsKey("error")) {
                    continue;
                }

                // Basic profile info
                employeeData.put("id", employeeId.toString());
                employeeData.put("full_name", profile.getFullName());
                employeeData.put("email", profile.getEmail());
                employeeData.put("role", profile.getRole());
                employeeData.put("department", profile.getDepartment());
                employeeData.put("status", profile.getStatus() != null ? profile.getStatus() : "active");

                Map<String, Object> pillars = mapValue(autoAura.get("pillars"));
                Map<String, Object> technical = mapValue(pillars != null ? pillars.get("technical") : null);
                Map<String, Object> behavioral = mapValue(pillars != null ? pillars.get("behavioral") : null);
                Map<String, Object> cultureFit = mapValue(pillars != null ? pillars.get("culture_fit") : null);
                Map<String, Object> growth = mapValue(pillars != null ? pillars.get("growth") : null);

                employeeData.put("technical_score", toFiveScale(doubleValue(technical != null ? technical.get("score") : null)));
                employeeData.put("behavioral_score", toFiveScale(doubleValue(behavioral != null ? behavioral.get("score") : null)));
                employeeData.put("culture_score", toFiveScale(doubleValue(cultureFit != null ? cultureFit.get("score") : null)));
                employeeData.put("growth_score", toFiveScale(doubleValue(growth != null ? growth.get("score") : null)));

                Double auraScore100 = doubleValue(autoAura.get("auraScore"));
                employeeData.put("aura_score", toFiveScale(auraScore100));
                employeeData.put("grade", stringValue(autoAura.get("grade")));

                // Get certificates count (approved)
                String quarter = getCurrentQuarter();
                int year = LocalDate.now().getYear();
                long certsCount = trainingRecordRepository.countApprovedInQuarter(employeeId, quarter, year);
                employeeData.put("certificates_count", certsCount);

                result.add(employeeData);
            } catch (Exception e) {
                // Skip employees that fail to calculate
                continue;
            }
        }

        return result;
    }

    private AuraDashboardDto.PillarScores buildPillarsFromAuto(Profile profile, Map<String, Object> autoAura) {
        AuraDashboardDto.PillarScores pillars = new AuraDashboardDto.PillarScores();
        DepartmentKpiConfig.DepartmentProfile profileConfig =
            DepartmentKpiConfig.getProfileForDepartment(profile.getDepartment());

        Map<String, Object> pillarMap = mapValue(autoAura.get("pillars"));
        pillars.setTechnical(buildPillarDetail(pillarMap, "technical", profileConfig));
        pillars.setBehavioral(buildPillarDetail(pillarMap, "behavioral", profileConfig));
        pillars.setCultureFit(buildPillarDetail(pillarMap, "culture_fit", profileConfig));
        pillars.setGrowthLearning(buildPillarDetail(pillarMap, "growth", profileConfig));
        return pillars;
    }

    private AuraDashboardDto.PillarDetail buildPillarDetail(
        Map<String, Object> pillarMap,
        String key,
        DepartmentKpiConfig.DepartmentProfile profileConfig
    ) {
        Map<String, Object> detail = pillarMap != null ? mapValue(pillarMap.get(key)) : null;
        DepartmentKpiConfig.PillarProfile pillarConfig = profileConfig.pillars.get(key);
        double weight = pillarConfig != null ? pillarConfig.weight : 0.0;

        AuraDashboardDto.PillarDetail pillarDetail = new AuraDashboardDto.PillarDetail();
        pillarDetail.setName(detail != null ? stringValue(detail.get("name")) : formatPillarName(key));
        Double score = doubleValue(detail != null ? detail.get("score") : null);
        pillarDetail.setScore(score != null ? Math.round(score * 10.0) / 10.0 : 0.0);
        pillarDetail.setWeight(weight);
        pillarDetail.setContribution(score != null ? Math.round(score * (weight / 100.0) * 100.0) / 100.0 : 0.0);
        pillarDetail.setDataSource(detail != null ? stringValue(detail.get("dataSource")) : "auto");
        return pillarDetail;
    }

    private String formatPillarName(String key) {
        if (key == null) return "Pillar";
        return switch (key) {
            case "culture_fit" -> "Culture Fit";
            case "growth" -> "Growth & Learning";
            default -> Character.toUpperCase(key.charAt(0)) + key.substring(1);
        };
    }

    private Double toFiveScale(Double score) {
        if (score == null) return null;
        return Math.round((score / 20.0) * 100.0) / 100.0;
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> casted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    casted.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return casted;
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
