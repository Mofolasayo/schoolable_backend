package com.schoolable.backend.kpi;

import com.schoolable.backend.profile.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Enhanced AI Service with RAG (Retrieval-Augmented Generation).
 * Provides rich context from multiple data sources before generating AI insights.
 * 
 * NOTE: This service is temporarily simplified. Full implementation requires
 * additional entity fields and repository methods to be added.
 */
@Service
public class EnhancedAiContextService {

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

        // TODO: Add integration with weekly reports, peer feedback, training records, and tasks
        // These require additional fields on the entities that are not yet implemented

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

        prompt.append("=== INSTRUCTIONS ===\n");
        prompt.append("Based on the quantitative metrics above, provide:\n");
        prompt.append("1. Performance Summary (2-3 sentences)\n");
        prompt.append("2. Top Strengths\n");
        prompt.append("3. Areas Needing Attention\n");
        prompt.append("4. Actionable Recommendations\n\n");
        prompt.append("Be objective but empathetic. Format your response as clear sections with bullet points.\n");

        return prompt.toString();
    }

    /**
     * Generate predictive insights for attrition risk.
     */
    public Map<String, Object> analyzeAttritionRisk(Profile employee) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Simplified implementation - returns low risk by default
        analysis.put("employeeId", employee.getId());
        analysis.put("employeeName", employee.getFullName());
        analysis.put("riskScore", 0);
        analysis.put("riskLevel", "LOW");
        analysis.put("riskFactors", new ArrayList<>());
        analysis.put("positiveFactors", List.of("Active employee"));
        analysis.put("recommendation", "No immediate concerns identified.");

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
