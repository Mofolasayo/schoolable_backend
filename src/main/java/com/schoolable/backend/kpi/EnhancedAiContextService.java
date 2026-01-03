package com.schoolable.backend.kpi;

import com.schoolable.backend.performance.WeeklyPerformanceReport;
import com.schoolable.backend.performance.WeeklyReportRepository;
import com.schoolable.backend.performance.PeerFeedback;
import com.schoolable.backend.performance.PeerFeedbackRepository;
import com.schoolable.backend.performance.TrainingRecord;
import com.schoolable.backend.performance.TrainingRecordRepository;
import com.schoolable.backend.task.Task;
import com.schoolable.backend.task.TaskRepository;
import com.schoolable.backend.profile.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced AI Service with RAG (Retrieval-Augmented Generation).
 * Provides rich context from multiple data sources before generating AI insights.
 */
@Service
public class EnhancedAiContextService {

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @Autowired
    private PeerFeedbackRepository peerFeedbackRepository;

    @Autowired
    private TrainingRecordRepository trainingRecordRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TeamKpiRepository teamKpiRepository;

    @Autowired
    private WeeklyKpiProgressRepository weeklyKpiProgressRepository;

    @Autowired
    private GeminiAiService geminiAiService;

    /**
     * Build rich context for an employee including qualitative data.
     */
    public EmployeeContext buildEmployeeContext(Profile employee, int weekNumber, int year) {
        EmployeeContext context = new EmployeeContext();
        context.employeeId = employee.getId();
        context.employeeName = employee.getFullName();
        context.department = employee.getDepartment();
        context.jobTitle = employee.getJobTitle();
        context.weekNumber = weekNumber;
        context.year = year;

        // Get weekly report highlights and challenges
        weeklyReportRepository.findByEmployeeIdAndWeekNumberAndYear(
            employee.getId(), weekNumber, year
        ).ifPresent(report -> {
            context.weeklyHighlights = report.getHighlights();
            context.weeklyChallenges = report.getChallenges();
            context.goalsForNextWeek = report.getGoalsForNextWeek();
            context.teamLeadComments = report.getTeamLeadComments();
            
            // Extract ratings from weekly report
            if (report.getInitiative() != null) {
                context.weeklyRatings.put("initiative", report.getInitiative());
            }
            if (report.getAttitude() != null) {
                context.weeklyRatings.put("attitude", report.getAttitude());
            }
            if (report.getTeamwork() != null) {
                context.weeklyRatings.put("teamwork", report.getTeamwork());
            }
            if (report.getCommunication() != null) {
                context.weeklyRatings.put("communication", report.getCommunication());
            }
            if (report.getProblemSolving() != null) {
                context.weeklyRatings.put("problemSolving", report.getProblemSolving());
            }
        });

        // Get recent peer feedback
        OffsetDateTime threeMonthsAgo = OffsetDateTime.now().minusMonths(3);
        List<PeerFeedback> recentFeedback = peerFeedbackRepository
            .findByRecipientIdAndCreatedAtAfterOrderByCreatedAtDesc(employee.getId(), threeMonthsAgo);
        
        for (PeerFeedback feedback : recentFeedback) {
            if (feedback.getStrengths() != null && !feedback.getStrengths().isEmpty()) {
                context.peerStrengths.add(feedback.getStrengths());
            }
            if (feedback.getAreasForImprovement() != null && !feedback.getAreasForImprovement().isEmpty()) {
                context.peerAreasForImprovement.add(feedback.getAreasForImprovement());
            }
            if (feedback.getOverallRating() != null) {
                context.peerRatings.add(feedback.getOverallRating());
            }
        }

        // Get recent training/certifications
        List<TrainingRecord> recentTraining = trainingRecordRepository
            .findByEmployeeIdAndCompletedAtAfterOrderByCompletedAtDesc(employee.getId(), threeMonthsAgo);
        
        for (TrainingRecord training : recentTraining) {
            context.recentTraining.add(training.getCertificateName());
        }

        // Get task metrics
        LocalDate weekStart = LocalDate.now()
            .with(WeekFields.ISO.weekOfYear(), weekNumber)
            .with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);
        
        List<Task> weekTasks = taskRepository.findByAssigneeIdAndDueDateBetween(
            employee.getId(), 
            weekStart.atStartOfDay().atOffset(java.time.ZoneOffset.UTC),
            weekEnd.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
        );

        long totalTasks = weekTasks.size();
        long completedTasks = weekTasks.stream()
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()))
            .count();
        long onTimeTasks = weekTasks.stream()
            .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()))
            .filter(t -> t.getCompletedAt() != null && t.getDueDate() != null)
            .filter(t -> !t.getCompletedAt().isAfter(t.getDueDate()))
            .count();

        context.taskMetrics.put("total", (int) totalTasks);
        context.taskMetrics.put("completed", (int) completedTasks);
        context.taskMetrics.put("onTime", (int) onTimeTasks);
        context.taskMetrics.put("completionRate", totalTasks > 0 ? (completedTasks * 100 / totalTasks) : 0);
        context.taskMetrics.put("onTimeRate", completedTasks > 0 ? (onTimeTasks * 100 / completedTasks) : 0);

        // Get task comments/notes for context
        for (Task task : weekTasks) {
            if (task.getComments() != null && !task.getComments().isEmpty()) {
                task.getComments().forEach(comment -> {
                    if (comment.getContent() != null && comment.getContent().length() > 20) {
                        context.taskComments.add(task.getTitle() + ": " + 
                            comment.getContent().substring(0, Math.min(100, comment.getContent().length())));
                    }
                });
            }
        }

        return context;
    }

    /**
     * Generate enhanced AI insight with RAG context.
     */
    public String generateEnhancedInsight(EmployeeContext context) {
        String prompt = buildEnhancedPrompt(context);
        
        try {
            return geminiAiService.generateContent(prompt);
        } catch (Exception e) {
            return "Unable to generate insight: " + e.getMessage();
        }
    }

    /**
     * Build an enhanced prompt with full context.
     */
    private String buildEnhancedPrompt(EmployeeContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an AI performance coach analyzing an employee's weekly performance.\n\n");
        
        prompt.append("=== EMPLOYEE INFORMATION ===\n");
        prompt.append("Name: ").append(context.employeeName).append("\n");
        prompt.append("Department: ").append(context.department).append("\n");
        prompt.append("Job Title: ").append(context.jobTitle).append("\n");
        prompt.append("Week: ").append(context.weekNumber).append(" of ").append(context.year).append("\n\n");

        prompt.append("=== QUANTITATIVE METRICS ===\n");
        prompt.append("Task Metrics:\n");
        prompt.append("- Total Tasks: ").append(context.taskMetrics.getOrDefault("total", 0)).append("\n");
        prompt.append("- Completed: ").append(context.taskMetrics.getOrDefault("completed", 0)).append("\n");
        prompt.append("- On-Time: ").append(context.taskMetrics.getOrDefault("onTime", 0)).append("\n");
        prompt.append("- Completion Rate: ").append(context.taskMetrics.getOrDefault("completionRate", 0)).append("%\n");
        prompt.append("- On-Time Rate: ").append(context.taskMetrics.getOrDefault("onTimeRate", 0)).append("%\n\n");

        if (!context.weeklyRatings.isEmpty()) {
            prompt.append("Weekly Ratings (1-5 scale):\n");
            context.weeklyRatings.forEach((key, value) -> 
                prompt.append("- ").append(key).append(": ").append(value).append("\n"));
            prompt.append("\n");
        }

        prompt.append("=== QUALITATIVE CONTEXT ===\n");
        
        if (context.weeklyHighlights != null && !context.weeklyHighlights.isEmpty()) {
            prompt.append("Employee's Weekly Highlights:\n\"").append(context.weeklyHighlights).append("\"\n\n");
        }
        
        if (context.weeklyChallenges != null && !context.weeklyChallenges.isEmpty()) {
            prompt.append("Challenges Faced:\n\"").append(context.weeklyChallenges).append("\"\n\n");
        }

        if (context.teamLeadComments != null && !context.teamLeadComments.isEmpty()) {
            prompt.append("Team Lead Comments:\n\"").append(context.teamLeadComments).append("\"\n\n");
        }

        if (!context.peerStrengths.isEmpty()) {
            prompt.append("Strengths Noted by Peers:\n");
            context.peerStrengths.forEach(s -> prompt.append("- ").append(s).append("\n"));
            prompt.append("\n");
        }

        if (!context.peerAreasForImprovement.isEmpty()) {
            prompt.append("Areas for Improvement (from peer feedback):\n");
            context.peerAreasForImprovement.forEach(s -> prompt.append("- ").append(s).append("\n"));
            prompt.append("\n");
        }

        if (!context.recentTraining.isEmpty()) {
            prompt.append("Recent Training/Certifications:\n");
            context.recentTraining.forEach(t -> prompt.append("- ").append(t).append("\n"));
            prompt.append("\n");
        }

        if (!context.taskComments.isEmpty()) {
            prompt.append("Notable Task Comments:\n");
            context.taskComments.stream().limit(5).forEach(c -> 
                prompt.append("- ").append(c).append("\n"));
            prompt.append("\n");
        }

        prompt.append("=== INSTRUCTIONS ===\n");
        prompt.append("Based on BOTH the quantitative metrics AND qualitative context above, provide:\n");
        prompt.append("1. Performance Summary (2-3 sentences considering both numbers and context)\n");
        prompt.append("2. Top Strengths (based on data and feedback)\n");
        prompt.append("3. Areas Needing Attention (be specific, reference the challenges mentioned)\n");
        prompt.append("4. Actionable Recommendations (prioritized, practical)\n");
        prompt.append("5. Risk Alerts (if any concerns about burnout, engagement, or performance trends)\n\n");
        prompt.append("Be objective but empathetic. Reference specific context when explaining insights.\n");
        prompt.append("Format your response as clear sections with bullet points.\n");

        return prompt.toString();
    }

    /**
     * Generate predictive insights for attrition risk.
     */
    public Map<String, Object> analyzeAttritionRisk(Profile employee) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Gather risk factors
        List<String> riskFactors = new ArrayList<>();
        List<String> positiveFactors = new ArrayList<>();
        int riskScore = 0;

        // Check engagement trends
        OffsetDateTime threeMonthsAgo = OffsetDateTime.now().minusMonths(3);
        List<WeeklyPerformanceReport> recentReports = weeklyReportRepository
            .findByEmployeeIdAndCreatedAtAfterOrderByCreatedAtDesc(employee.getId(), threeMonthsAgo);

        if (recentReports.isEmpty()) {
            riskFactors.add("No recent performance reports - possible disengagement");
            riskScore += 20;
        } else {
            // Check for declining ratings
            List<Double> ratings = recentReports.stream()
                .map(r -> (double) ((r.getInitiative() != null ? r.getInitiative() : 0) +
                                   (r.getAttitude() != null ? r.getAttitude() : 0)) / 2)
                .collect(Collectors.toList());
            
            if (ratings.size() >= 3) {
                double recent = ratings.subList(0, Math.min(2, ratings.size())).stream()
                    .mapToDouble(d -> d).average().orElse(0);
                double older = ratings.subList(Math.min(2, ratings.size()), ratings.size()).stream()
                    .mapToDouble(d -> d).average().orElse(0);
                
                if (older > 0 && recent < older * 0.8) {
                    riskFactors.add("Declining performance trend observed");
                    riskScore += 15;
                }
            }
        }

        // Check task completion rates
        List<Task> recentTasks = taskRepository.findByAssigneeIdAndCreatedAtAfter(
            employee.getId(), threeMonthsAgo);
        
        if (!recentTasks.isEmpty()) {
            long completed = recentTasks.stream()
                .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()))
                .count();
            double completionRate = (double) completed / recentTasks.size() * 100;
            
            if (completionRate < 50) {
                riskFactors.add("Low task completion rate (" + String.format("%.0f", completionRate) + "%)");
                riskScore += 15;
            } else if (completionRate > 90) {
                positiveFactors.add("High task completion rate");
            }
        }

        // Check peer feedback sentiment
        List<PeerFeedback> recentFeedback = peerFeedbackRepository
            .findByRecipientIdAndCreatedAtAfterOrderByCreatedAtDesc(employee.getId(), threeMonthsAgo);
        
        if (!recentFeedback.isEmpty()) {
            double avgRating = recentFeedback.stream()
                .filter(f -> f.getOverallRating() != null)
                .mapToDouble(PeerFeedback::getOverallRating)
                .average().orElse(3.0);
            
            if (avgRating < 2.5) {
                riskFactors.add("Below average peer ratings");
                riskScore += 10;
            } else if (avgRating > 4.0) {
                positiveFactors.add("Strong peer relationships");
            }
        }

        // Check training engagement
        List<TrainingRecord> recentTraining = trainingRecordRepository
            .findByEmployeeIdAndCompletedAtAfterOrderByCompletedAtDesc(employee.getId(), threeMonthsAgo);
        
        if (recentTraining.isEmpty()) {
            riskFactors.add("No training or skill development activity");
            riskScore += 5;
        } else {
            positiveFactors.add("Active in professional development");
        }

        // Calculate risk level
        String riskLevel;
        if (riskScore >= 40) {
            riskLevel = "HIGH";
        } else if (riskScore >= 20) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        analysis.put("employeeId", employee.getId());
        analysis.put("employeeName", employee.getFullName());
        analysis.put("riskScore", riskScore);
        analysis.put("riskLevel", riskLevel);
        analysis.put("riskFactors", riskFactors);
        analysis.put("positiveFactors", positiveFactors);

        // Generate AI summary if high risk
        if (riskScore >= 20) {
            String prompt = "Based on these risk factors for employee " + employee.getFullName() + ": " +
                String.join(", ", riskFactors) + ". " +
                "And positive factors: " + String.join(", ", positiveFactors) + ". " +
                "Provide a brief 2-3 sentence recommendation for HR/management to address retention risk.";
            
            try {
                String recommendation = geminiAiService.generateContent(prompt);
                analysis.put("recommendation", recommendation);
            } catch (Exception e) {
                analysis.put("recommendation", "Unable to generate AI recommendation");
            }
        }

        return analysis;
    }

    /**
     * Context object for RAG-enhanced insights.
     */
    public static class EmployeeContext {
        public UUID employeeId;
        public String employeeName;
        public String department;
        public String jobTitle;
        public int weekNumber;
        public int year;
        
        // Weekly report context
        public String weeklyHighlights;
        public String weeklyChallenges;
        public String goalsForNextWeek;
        public String teamLeadComments;
        
        // Ratings
        public Map<String, Integer> weeklyRatings = new HashMap<>();
        public List<Integer> peerRatings = new ArrayList<>();
        
        // Qualitative feedback
        public List<String> peerStrengths = new ArrayList<>();
        public List<String> peerAreasForImprovement = new ArrayList<>();
        
        // Training
        public List<String> recentTraining = new ArrayList<>();
        
        // Task data
        public Map<String, Integer> taskMetrics = new HashMap<>();
        public List<String> taskComments = new ArrayList<>();
    }
}
