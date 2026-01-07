package com.schoolable.backend.kpi;

import com.schoolable.backend.attendance.Attendance;
import com.schoolable.backend.attendance.AttendanceRepository;
import com.schoolable.backend.performance.TrainingRecordRepository;
import com.schoolable.backend.performance.WeeklyPerformanceReport;
import com.schoolable.backend.performance.WeeklyReportRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.task.Task;
import com.schoolable.backend.task.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Personal AI Insights Service
 * Generates personalized performance insights for individual employees using Gemini AI
 */
@Service
public class PersonalInsightsService {

    @Autowired
    private GeminiAiService geminiService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TrainingRecordRepository trainingRepository;

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private com.schoolable.backend.announcement.AnnouncementReadRepository announcementReadRepository;

    @Autowired
    private com.schoolable.backend.performance.PeerFeedbackRepository peerFeedbackRepository;

    @Autowired
    private com.schoolable.backend.performance.AuraDashboardService auraDashboardService;

    public Map<String, Object> generatePersonalInsights(UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId).orElse(null);
        if (profile == null) {
            return Map.of("error", "Employee not found");
        }

        // Gather all performance data
        Map<String, Object> performanceData = gatherPerformanceData(employeeId, profile);
        
        // Generate AI insights
        String aiInsights = generateAiPersonalInsights(profile, performanceData);
        
        // Parse and return structured response
        return parsePersonalInsights(aiInsights, performanceData, profile);
    }

    private Map<String, Object> gatherPerformanceData(UUID employeeId, Profile profile) {
        LocalDate now = LocalDate.now();
        LocalDate quarterStart = getQuarterStart(now);
        LocalDate prevQuarterStart = quarterStart.minusMonths(3);
        OffsetDateTime quarterStartTime = quarterStart.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);

        Map<String, Object> data = new HashMap<>();

        // ... (existing task logic) ...
        // Task Performance
        List<Task> allTasks = taskRepository.findByAssigneeIdOrderByCreatedAtDesc(employeeId);
        List<Task> quarterTasks = allTasks.stream()
            .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().toLocalDate().isBefore(quarterStart))
            .toList();

        long completedTasks = quarterTasks.stream()
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()) || "Done".equalsIgnoreCase(t.getStatus()))
            .count();

        long onTimeTasks = quarterTasks.stream()
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()) || "Done".equalsIgnoreCase(t.getStatus()))
            .filter(t -> t.getDueDate() != null && t.getUpdatedAt() != null)
            .filter(t -> !t.getUpdatedAt().toLocalDate().isAfter(t.getDueDate().toLocalDate()))
            .count();
        
        // HISTORICAL TRENDS: Previous Quarter
        List<Task> prevQuarterTasks = allTasks.stream()
            .filter(t -> t.getCreatedAt() != null && 
                         !t.getCreatedAt().toLocalDate().isBefore(prevQuarterStart) && 
                         t.getCreatedAt().toLocalDate().isBefore(quarterStart))
            .toList();
            
        long prevCompleted = prevQuarterTasks.stream()
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus())).count();
            
        double currentCompletionRate = quarterTasks.isEmpty() ? 0 : (completedTasks * 100.0 / quarterTasks.size());
        double prevCompletionRate = prevQuarterTasks.isEmpty() ? 0 : (prevCompleted * 100.0 / prevQuarterTasks.size());
        
        data.put("prevCompletionRate", prevCompletionRate);
        data.put("completionTrend", currentCompletionRate - prevCompletionRate);

        // RICH CONTEXT: Recent Tasks
        List<String> recentCompletedTitles = quarterTasks.stream()
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()))
            .limit(5)
            .map(Task::getTitle)
            .collect(Collectors.toList());
            
        List<String> pendingTaskTitles = quarterTasks.stream()
            .filter(t -> !"Completed".equalsIgnoreCase(t.getStatus()) && !"Done".equalsIgnoreCase(t.getStatus()))
            .limit(3)
            .map(Task::getTitle)
            .collect(Collectors.toList());
            
        data.put("recentCompletedTasks", String.join(", ", recentCompletedTitles));
        data.put("pendingTasks", String.join(", ", pendingTaskTitles));

        // Quality ratings received
        Double avgRating = taskRepository.getAverageQualityRatingAfter(employeeId, quarterStartTime);
        long ratedTasks = taskRepository.countRatedTasksAfter(employeeId, quarterStartTime);

        // Response time (average days from creation to first update)
        List<Task> completedWithDates = quarterTasks.stream()
            .filter(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null)
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()))
            .toList();
        
        double avgResponseDays = completedWithDates.isEmpty() ? 0 :
            completedWithDates.stream()
                .mapToLong(t -> ChronoUnit.DAYS.between(t.getCreatedAt().toLocalDate(), t.getUpdatedAt().toLocalDate()))
                .average()
                .orElse(0);

        data.put("totalTasks", quarterTasks.size());
        data.put("completedTasks", completedTasks);
        data.put("completionRate", currentCompletionRate);
        data.put("onTimeTasks", onTimeTasks);
        data.put("onTimeRate", completedTasks == 0 ? 0 : (onTimeTasks * 100.0 / completedTasks));
        data.put("avgQualityRating", avgRating != null ? avgRating : 0);
        data.put("ratedTasks", ratedTasks);
        data.put("avgResponseDays", avgResponseDays);

        // Attendance
        List<Attendance> attendances = attendanceRepository.findByUserIdOrderByDateDesc(employeeId);
        List<Attendance> quarterAttendances = attendances.stream()
            .filter(a -> a.getDate() != null && !a.getDate().isBefore(quarterStart))
            .toList();

        long onTimeCheckIns = quarterAttendances.stream()
            .filter(a -> a.getCheckIn() != null)
            .filter(a -> a.getCheckIn().getHour() < 9 || (a.getCheckIn().getHour() == 9 && a.getCheckIn().getMinute() == 0))
            .count();

        data.put("attendanceDays", quarterAttendances.size());
        data.put("onTimeCheckIns", onTimeCheckIns);
        data.put("punctualityRate", quarterAttendances.isEmpty() ? 0 : (onTimeCheckIns * 100.0 / quarterAttendances.size()));

        // Training
        String currentQuarter = getCurrentQuarter();
        int currentYear = now.getYear();
        long quarterCerts = trainingRepository.countApprovedInQuarter(employeeId, currentQuarter, currentYear);
        data.put("certificatesThisQuarter", quarterCerts);

        // RICH CONTEXT: Announcements
        long totalAnnouncements = announcementReadRepository.countTotalAnnouncementsAfter(quarterStartTime);
        long readAnnouncements = announcementReadRepository.countByUserIdAndReadAtAfter(employeeId, quarterStartTime);
        data.put("totalAnnouncements", totalAnnouncements);
        data.put("readAnnouncements", readAnnouncements);

        // Team Lead ratings & RICH CONTEXT: Manager Feedback
        List<WeeklyPerformanceReport> reports = weeklyReportRepository.findByEmployeeIdOrderByYearDescWeekNumberDesc(employeeId);
        String managerFeedback = "None";
        if (!reports.isEmpty()) {
            WeeklyPerformanceReport latest = reports.get(0);
            data.put("latestInitiativeScore", latest.getInitiativeScore());
            data.put("latestAttitudeScore", latest.getAttitudeTowardsWorkScore());
            data.put("latestTeamworkScore", latest.getTeamworkCollaborationScore());
            
            String highlights = latest.getWeeklyHighlights() != null ? latest.getWeeklyHighlights() : "";
            String areas = latest.getAreasForFocus() != null ? latest.getAreasForFocus() : "";
            if (!highlights.isEmpty() || !areas.isEmpty()) {
                managerFeedback = "Highlights: " + highlights + " | Focus Areas: " + areas;
            }
        }
        data.put("managerFeedback", managerFeedback);

        // Peer Feedback (NEW) - Get feedback comments for qualitative analysis
        try {
            List<com.schoolable.backend.performance.PeerFeedback> feedback = peerFeedbackRepository
                .findByToEmployeeIdAndQuarterAndYear(employeeId, currentQuarter, currentYear);
            
            List<String> feedbackComments = feedback.stream()
                .map(f -> {
                    String s = f.getStrengths() != null ? "Strengths: " + f.getStrengths() : "";
                    String i = f.getAreasForImprovement() != null ? "Improvement: " + f.getAreasForImprovement() : "";
                    return (s + " " + i).trim();
                })
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.toList());
            
            data.put("feedbackComments", feedbackComments);
        } catch (Exception e) {
            data.put("feedbackComments", Collections.emptyList());
        }

        // TEAM CONTEXT: Calculate Department Average (Simplified for MVP)
        // We assume roughly 75% as a baseline if calculation is expensive, or calculate simply
        // Ideally: cache this. For now, we'll set a static baseline or skip expensive query
        data.put("teamAvgCompletion", 75.0); // Placeholder for team benchmark
        
        // Strengths and areas for improvement (simplified analysis)
        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();

        if (currentCompletionRate >= 80) strengths.add("High task completion rate");
        else if (currentCompletionRate < 50) improvements.add("Task completion rate needs improvement");
        
        if ((double) data.get("completionTrend") > 10) strengths.add("Significant improvement in task output vs last quarter");
        else if ((double) data.get("completionTrend") < -10) improvements.add("Task output has dropped compared to last quarter");

        if ((double) data.get("onTimeRate") >= 90) strengths.add("Excellent deadline adherence");
        else if ((double) data.get("onTimeRate") < 70) improvements.add("On-time delivery needs focus");

        if ((double) data.get("punctualityRate") >= 90) strengths.add("Consistent punctuality");
        else if ((double) data.get("punctualityRate") < 70) improvements.add("Punctuality could be improved");

        if (avgRating != null && avgRating >= 4.0) strengths.add("High quality work recognized by peers");
        else if (avgRating != null && avgRating < 3.0) improvements.add("Work quality ratings need attention");

        data.put("strengths", strengths);
        data.put("improvements", improvements);
        data.put("department", profile.getDepartment());
        data.put("employeeName", profile.getFullName());

        return data;
    }

    private String generateAiPersonalInsights(Profile profile, Map<String, Object> data) {
        String prompt = String.format("""
            You are a performance coach generating personalized insights for an employee.
            
            EMPLOYEE: %s %s
            DEPARTMENT: %s
            
            PERFORMANCE DATA:
            - Task Completion Rate: %.1f%% (%d of %d tasks completed)
            - On-Time Delivery: %.1f%%
            - Average Quality Rating: %.1f/5 (%d rated tasks)
            - Response Time: %.1f days
            - Attendance: %d days (%.1f%% punctuality)
            - Certificates: %d
            - Announcement Engagement: Read %d of %d announcements
            
            TREND ANALYSIS (Vs Last Quarter):
            - Completion Rate Change: %.1f%% (Prev: %.1f%% -> Curr: %.1f%%)
            %s
            
            TEAM CONTEXT:
            - Department Average Completion: %.1f%%
            
            RECENT WORK CONTEXT:
            - Recently Completed Tasks: %s
            - Pending Tasks: %s
            
            MANAGER FEEDBACK (Weekly Reports):
            %s
            
            PEER FEEDBACK:
            %s
            
            IDENTIFIED STRENGTHS: %s
            AREAS FOR IMPROVEMENT: %s
            
            Generate a personalized performance summary in JSON format:
            {
                "overallAssessment": "2-3 sentence overall performance summary. Mention trends (improving/declining).",
                "performanceScore": <0-100 calculated score>,
                "keyStrengths": ["strength1", "strength2", "strength3"],
                "improvementAreas": ["area1", "area2"],
                "actionableRecommendations": ["recommendation1", "recommendation2", "recommendation3"],
                "skillsToFocus": ["skill1", "skill2"],
                "motivationalMessage": "personalized encouraging message referencing recent work and trends"
            }
            
            Be specific, constructive, and encouraging. Focus on actionable advice.
            Return ONLY valid JSON, no markdown.
            """,
            profile.getFullName() != null ? profile.getFullName().split(" ")[0] : "", 
            profile.getFullName() != null && profile.getFullName().contains(" ") ? profile.getFullName().substring(profile.getFullName().indexOf(" ") + 1) : "",
            profile.getDepartment(),
            (double) data.get("completionRate"),
            (long) data.get("completedTasks"),
            (int) data.get("totalTasks"),
            (double) data.get("onTimeRate"),
            (double) data.get("avgQualityRating"),
            (long) data.get("ratedTasks"),
            (double) data.get("avgResponseDays"),
            (int) data.get("attendanceDays"),
            (double) data.get("punctualityRate"),
            (long) data.get("certificatesThisQuarter"),
            (long) data.get("readAnnouncements"),
            (long) data.get("totalAnnouncements"),
            (double) data.get("completionTrend"),
            (double) data.get("prevCompletionRate"),
            (double) data.get("completionRate"),
            (double) data.get("completionTrend") > 0 ? "- Positive Momentum Detected" : ((double) data.get("completionTrend") < -10 ? "- WARNING: Performance dip detected" : "- Stable"),
            (double) data.get("teamAvgCompletion"),
            data.get("recentCompletedTasks"),
            data.get("pendingTasks"),
            data.get("managerFeedback"),
            data.get("feedbackComments") != null ? data.get("feedbackComments").toString() : "None",
            data.get("strengths"),
            data.get("improvements")
        );

        return geminiService.generateContent(prompt);
    }

    private Map<String, Object> parsePersonalInsights(String aiResponse, Map<String, Object> performanceData, Profile profile) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("employeeId", profile.getId());
        result.put("employeeName", profile.getFullName());
        result.put("department", profile.getDepartment());
        result.put("generatedAt", OffsetDateTime.now());
        result.put("performanceData", performanceData);
        
        try {
            // Clean the AI response
            String cleanJson = aiResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            // Parse JSON using basic parsing
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> aiInsights = mapper.readValue(cleanJson, Map.class);
            result.put("aiInsights", aiInsights);
        } catch (Exception e) {
            // Fallback if AI parsing fails
            result.put("aiInsights", Map.of(
                "overallAssessment", "Performance data collected - AI analysis pending",
                "performanceScore", calculateBasicScore(performanceData),
                "keyStrengths", performanceData.get("strengths"),
                "improvementAreas", performanceData.get("improvements"),
                "actionableRecommendations", List.of(
                    "Focus on completing assigned tasks",
                    "Maintain consistent attendance",
                    "Seek feedback from your team lead"
                ),
                "skillsToFocus", List.of("Time Management", "Communication"),
                "motivationalMessage", "Keep up the good work and continue striving for excellence!"
            ));
            result.put("aiError", e.getMessage());
        }
        
        return result;
    }

    private int calculateBasicScore(Map<String, Object> data) {
        double completionWeight = 0.30;
        double onTimeWeight = 0.25;
        double qualityWeight = 0.20;
        double punctualityWeight = 0.15;
        double trainingWeight = 0.10;

        double completionScore = (double) data.get("completionRate");
        double onTimeScore = (double) data.get("onTimeRate");
        double qualityScore = ((double) data.get("avgQualityRating") / 5.0) * 100;
        double punctualityScore = (double) data.get("punctualityRate");
        double trainingScore = ((long) data.get("certificatesThisQuarter") > 0) ? 100 : 0;

        return (int) (completionScore * completionWeight +
                     onTimeScore * onTimeWeight +
                     qualityScore * qualityWeight +
                     punctualityScore * punctualityWeight +
                     trainingScore * trainingWeight);
    }

    private LocalDate getQuarterStart(LocalDate date) {
        int month = date.getMonthValue();
        int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), quarterStartMonth, 1);
    }

    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }
}
