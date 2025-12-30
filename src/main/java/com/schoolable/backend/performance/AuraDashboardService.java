package com.schoolable.backend.performance;

import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.task.TaskRepository;
import com.schoolable.backend.attendance.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service for calculating and retrieving Aura scores for employees.
 * Combines auto-calculated metrics with team lead weekly ratings.
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

        // Calculate each pillar
        AuraDashboardDto.PillarScores pillars = new AuraDashboardDto.PillarScores();

        // Technical Pillar (Auto-calculated from tasks)
        AuraDashboardDto.PillarDetail technical = calculateTechnicalPillar(employeeId);
        pillars.setTechnical(technical);

        // Behavioral Pillar (Mixed: Team Lead + Auto)
        AuraDashboardDto.PillarDetail behavioral = calculateBehavioralPillar(employeeId);
        pillars.setBehavioral(behavioral);

        // Culture Fit Pillar (Mixed: Team Lead + Auto)
        AuraDashboardDto.PillarDetail cultureFit = calculateCultureFitPillar(employeeId);
        pillars.setCultureFit(cultureFit);

        // Growth & Learning Pillar (Placeholder for now)
        AuraDashboardDto.PillarDetail growth = new AuraDashboardDto.PillarDetail();
        growth.setName("Growth & Learning");
        growth.setScore(60.0); // Placeholder
        growth.setWeight(25.0);
        growth.setContribution(15.0);
        growth.setDataSource("pending");
        pillars.setGrowthLearning(growth);

        // Collaboration Pillar (Placeholder for now)
        AuraDashboardDto.PillarDetail collaboration = new AuraDashboardDto.PillarDetail();
        collaboration.setName("Collaboration");
        collaboration.setScore(65.0); // Placeholder
        collaboration.setWeight(25.0);
        collaboration.setContribution(16.25);
        collaboration.setDataSource("pending");
        pillars.setCollaboration(collaboration);

        response.setPillars(pillars);

        // Calculate overall Aura score
        double auraScore = 
            technical.getContribution() +
            behavioral.getContribution() +
            cultureFit.getContribution() +
            growth.getContribution() / 2 +      // Half weight for unimplemented
            collaboration.getContribution() / 2; // Half weight for unimplemented
        
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
     * Components:
     * - Company Values (5%) - Manager assessment
     * - Work Ethics (5%) - Auto (disciplinary records)
     * - Accountability (5%) - Auto (task ownership)
     * - Attitude Towards Work (5%) - Team Lead
     * - Policy Compliance (5%) - Auto (attendance violations)
     */
    private AuraDashboardDto.PillarDetail calculateCultureFitPillar(UUID employeeId) {
        AuraDashboardDto.PillarDetail pillar = new AuraDashboardDto.PillarDetail();
        pillar.setName("Culture Fit");
        pillar.setWeight(25.0);
        pillar.setDataSource("mixed");

        LocalDate quarterStart = getQuarterStartDate();
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);

        // Attitude from Team Lead
        double attitudeScore = 50.0;
        if (!reports.isEmpty()) {
            attitudeScore = reports.stream()
                .filter(r -> r.getAttitudeTowardsWorkScore() != null)
                .mapToInt(WeeklyPerformanceReport::getAttitudeTowardsWorkScore)
                .average()
                .orElse(2.5) * 20;
        }

        // Work ethics (no disciplinary actions = 100%)
        double workEthicsScore = 100.0; // Placeholder, would check disciplinary_actions table

        // Accountability from task completion
        OffsetDateTime quarterStartOdt = quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        long totalTasks = taskRepository.countByAssigneeIdAndCreatedAtAfter(employeeId, quarterStartOdt);
        long completedTasks = taskRepository.countByAssigneeIdAndStatusAndCreatedAtAfter(employeeId, "Completed", quarterStartOdt);
        double accountabilityScore = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 60.0;

        // Company values (manager assessment placeholder)
        double valuesScore = 70.0;

        // Policy compliance from attendance
        double complianceScore = 85.0; // Placeholder

        // Calculate weighted average
        double cultureFitScore = (
            valuesScore * 0.2 +
            workEthicsScore * 0.2 +
            accountabilityScore * 0.2 +
            attitudeScore * 0.2 +
            complianceScore * 0.2
        );

        pillar.setScore(Math.round(cultureFitScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(cultureFitScore * 0.25 * 100.0) / 100.0);

        return pillar;
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
