package com.schoolable.backend.performance;

import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.task.TaskRepository;
import com.schoolable.backend.attendance.AttendanceRepository;
import com.schoolable.backend.messaging.MessageRepository;
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
    private MessageRepository messageRepository;

    @Autowired
    private TrainingRecordRepository trainingRecordRepository;

    @Autowired
    private PeerFeedbackRepository peerFeedbackRepository;

    @Autowired
    private AnnouncementReadRepository announcementReadRepository;

    /**
     * Get the full Aura dashboard data for an employee.
     * This is the main endpoint for the mobile app.
     */
    public AuraDashboardDto.EmployeeAuraResponse getEmployeeAuraDashboard(UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        AuraDashboardDto.EmployeeAuraResponse response = new AuraDashboardDto.EmployeeAuraResponse();
        response.setEmployeeId(employeeId.toString());
        response.setEmployeeName(profile.getFullName());
        response.setDepartment(profile.getDepartment());
        response.setRole(profile.getRole());

        // Get current quarter info
        LocalDate now = LocalDate.now();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;
        response.setCurrentQuarter("Q" + currentQuarter);
        response.setCurrentYear(now.getYear());

        // Calculate each pillar (4 pillars × 25% each)
        AuraDashboardDto.PillarScores pillars = new AuraDashboardDto.PillarScores();

        // Technical Pillar (25% - Auto-calculated from tasks)
        AuraDashboardDto.PillarDetail technical = calculateTechnicalPillar(employeeId);
        pillars.setTechnical(technical);

        // Behavioral Pillar (25% - Mixed: Team Lead + Auto)
        AuraDashboardDto.PillarDetail behavioral = calculateBehavioralPillar(employeeId);
        pillars.setBehavioral(behavioral);

        // Culture Fit Pillar (25% - Mixed: Team Lead + Auto + Collaboration metrics)
        AuraDashboardDto.PillarDetail cultureFit = calculateCultureFitPillar(employeeId);
        pillars.setCultureFit(cultureFit);

        // Growth & Learning Pillar (25% - Uses training records)
        AuraDashboardDto.PillarDetail growth = calculateGrowthPillar(employeeId);
        pillars.setGrowthLearning(growth);

        response.setPillars(pillars);

        // Calculate overall Aura score (4 pillars × 25% each = 100%)
        double auraScore = 
            technical.getContribution() +
            behavioral.getContribution() +
            cultureFit.getContribution() +
            growth.getContribution();
        
        response.setAuraScore(Math.round(auraScore * 100.0) / 100.0);
        response.setQgpa(Math.round((auraScore / 20) * 100.0) / 100.0);
        response.setGrade(calculateGrade(auraScore));

        // Get weeks rated count
        int weeksRated = getWeeksRatedThisQuarter(employeeId, currentQuarter, now.getYear());
        response.setWeeksRatedThisQuarter(weeksRated);

        return response;
    }

    /**
     * Calculate Technical Competence pillar (25%)
     * Based on task completion and on-time delivery
     */
    private AuraDashboardDto.PillarDetail calculateTechnicalPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Technical Competence");
        pillar.setWeight(25.0);
        pillar.setDataSource("auto");

        LocalDate quarterStart = getQuarterStartDate();
        
        // Task completion rate
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        long totalTasks = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long completedTasks = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStartOdt);
        
        double completionRate = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 50.0;
        
        // On-time delivery rate (simplified - assumes completed tasks are on time)
        // In real implementation, would compare updated_at to due_date
        double onTimeRate = completionRate * 0.9; // Estimate 90% of completed are on time
        
        double technicalScore = (completionRate + onTimeRate) / 2;
        pillar.setScore(Math.round(technicalScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(technicalScore * 0.25 * 100.0) / 100.0);

        return pillar;
    }

    /**
     * Calculate Behavioral Competence pillar (25%)
     * Components:
     * - Teamwork & Collaboration (5%) - Team Lead
     * - Initiative (5%) - Team Lead
     * - Professionalism (5%) - Auto (attendance)
     * - Time Management (5%) - Auto (task deadlines)
     * - Adaptability (5%) - Manager assessment (placeholder)
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

        if (!reports.isEmpty()) {
            // Average team lead ratings (convert 1-5 to 0-100)
            teamworkScore = reports.stream()
                .filter(r -> r.getTeamworkCollaborationScore() != null)
                .mapToInt(WeeklyPerformanceReport::getTeamworkCollaborationScore)
                .average()
                .orElse(2.5) * 20;

            initiativeScore = reports.stream()
                .filter(r -> r.getInitiativeScore() != null)
                .mapToInt(WeeklyPerformanceReport::getInitiativeScore)
                .average()
                .orElse(2.5) * 20;
        }

        // Professionalism from attendance
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        long totalAttendance = attendanceRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long presentCount = attendanceRepository.countByUserIdAndStatusAndCreatedAtAfter(employeeId, "present", quarterStartOdt);
        double professionalismScore = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 70.0;

        // Time management from tasks
        double timeManagementScore = 70.0; // Placeholder, would calculate from due_date vs completion

        // Adaptability (manager assessment placeholder)
        double adaptabilityScore = 60.0;

        // Calculate weighted average
        double behavioralScore = (
            teamworkScore * 0.2 +       // 5/25 = 20%
            initiativeScore * 0.2 +     // 5/25 = 20%
            professionalismScore * 0.2 + // 5/25 = 20%
            timeManagementScore * 0.2 +  // 5/25 = 20%
            adaptabilityScore * 0.2      // 5/25 = 20%
        );

        pillar.setScore(Math.round(behavioralScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(behavioralScore * 0.25 * 100.0) / 100.0);

        return pillar;
    }

    /**
     * Calculate Culture Fit pillar (25%)
     * Components (now includes collaboration):
     * - Company Values (5%) - Manager assessment
     * - Attitude Towards Work (5%) - Team Lead  
     * - Teamwork & Collaboration (5%) - Team Lead + Peer Feedback
     * - Communication & Engagement (5%) - Auto (announcement reads, messaging)
     * - Policy Compliance (5%) - Auto (attendance, punctuality)
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

        // 2. Teamwork & Collaboration (20% of pillar - Team Lead + Peer Feedback)
        double teamworkScore = 50.0;
        if (!reports.isEmpty()) {
            teamworkScore = reports.stream()
                .filter(r -> r.getTeamworkCollaborationScore() != null)
                .mapToInt(WeeklyPerformanceReport::getTeamworkCollaborationScore)
                .average()
                .orElse(2.5) * 20;
        }
        
        // Boost teamwork with peer feedback if available
        double peerScore = getPeerFeedbackScore(employeeId, quarterStartOdt);
        if (peerScore > 0) {
            teamworkScore = (teamworkScore + peerScore) / 2; // Average with peer feedback
        }

        // 3. Communication & Engagement (20% of pillar - Auto)
        double engagementScore = getAnnouncementEngagementScore(employeeId, quarterStartOdt);

        // 4. Company Values (20% of pillar - placeholder)
        double valuesScore = 70.0;

        // 5. Policy Compliance (20% of pillar - Auto from attendance)
        long totalAttendance = attendanceRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long presentCount = attendanceRepository.countByUserIdAndStatusAndCreatedAtAfter(employeeId, "present", quarterStartOdt);
        double complianceScore = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 70.0;

        // Calculate weighted average (20% each component)
        double cultureFitScore = (
            attitudeScore * 0.2 +
            teamworkScore * 0.2 +
            engagementScore * 0.2 +
            valuesScore * 0.2 +
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

        // Communication score (10%): Based on messages sent this quarter
        // Scoring: 0 = 20%, 1-9 = 40%, 10-29 = 60%, 30-59 = 80%, 60+ = 100%
        long userMessages = messageRepository.countByUserIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        double communicationScore;
        if (userMessages >= 60) {
            communicationScore = 100.0;
        } else if (userMessages >= 30) {
            communicationScore = 80.0;
        } else if (userMessages >= 10) {
            communicationScore = 60.0;
        } else if (userMessages > 0) {
            communicationScore = 40.0;
        } else {
            communicationScore = 20.0;
        }

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
     * - Training Completion (10%) - AUTO from training_records
     * - Skill Application (5%) - Task complexity increase (placeholder)
     * - Knowledge Sharing (10%) - Announcements/docs created (placeholder)
     */
    private AuraDashboardDto.PillarDetail calculateGrowthPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Growth & Learning");
        pillar.setWeight(25.0);
        pillar.setDataSource("mixed");

        LocalDate quarterStart = getQuarterStartDate();

        // Training Completion: Count completed trainings this quarter
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

        // Skill Application (placeholder - would need task complexity analysis)
        double skillApplicationScore = 60.0;

        // Knowledge Sharing (placeholder - would need announcements/attachments count)
        double knowledgeSharingScore = 60.0;

        // Calculate weighted average (training is 40% of pillar, others 30% each)
        double growthScore = (
            trainingScore * 0.4 +            // 10/25 = 40%
            skillApplicationScore * 0.2 +    // 5/25 = 20%
            knowledgeSharingScore * 0.4      // 10/25 = 40%
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
}


