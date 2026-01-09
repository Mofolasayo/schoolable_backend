package com.schoolable.backend.kpi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Gemini AI Service
 * Handles all AI-powered analysis for KPIs and team performance
 */
@Service
public class GeminiAiService {

    @Value("${gemini.api.key:AIzaSyBu8oPRazR8zn1A0DLmgBjVIl6KaSN5UZE}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Analyze weekly KPI progress and generate insights
     */
    public AiAnalysisResult analyzeWeeklyProgress(
            String teamName,
            String department,
            List<KpiProgressData> kpiData,
            int weekNumber,
            int year) {

        String prompt = buildWeeklyAnalysisPrompt(teamName, department, kpiData, weekNumber, year);
        String aiResponse = callGeminiApi(prompt);
        
        return parseAiResponse(aiResponse, kpiData);
    }

    /**
     * Generate quarterly summary and team score
     */
    public AiAnalysisResult analyzeQuarterlyPerformance(
            String teamName,
            String department,
            List<KpiProgressData> kpiData,
            String quarter,
            int year) {

        String prompt = buildQuarterlyAnalysisPrompt(teamName, department, kpiData, quarter, year);
        String aiResponse = callGeminiApi(prompt);
        
        return parseAiResponse(aiResponse, kpiData);
    }

    /**
     * Generate content from a custom prompt (public wrapper for callGeminiApi)
     */
    public String generateContent(String prompt) {
        return callGeminiApi(prompt);
    }

    /**
     * Grade a daily report and provide feedback
     * Used for the "Technical Competence" pillar scoring
     */
    public DailyReportGradingResult gradeDailyReport(
            String employeeName,
            String department,
            String tasksCompleted,
            String tasksInProgress,
            String blockers,
            String plannedForTomorrow,
            String additionalNotes,
            List<String> individualKpis) {

        // Pre-check for empty/insufficient content - assign low score automatically
        DailyReportGradingResult preCheckResult = preValidateReportContent(
            tasksCompleted, tasksInProgress, blockers, plannedForTomorrow
        );
        if (preCheckResult != null) {
            return preCheckResult;
        }

        String prompt = buildDailyReportGradingPrompt(
                employeeName, department, tasksCompleted, tasksInProgress,
                blockers, plannedForTomorrow, additionalNotes, individualKpis);
        
        String aiResponse = callGeminiApi(prompt);
        return parseDailyReportGradingResponse(aiResponse);
    }

    /**
     * Pre-validate report content before calling AI
     * Returns a zero/very low score if content is clearly insufficient
     * Empty reports are REJECTED with 0% score
     */
    private DailyReportGradingResult preValidateReportContent(
            String tasksCompleted, String tasksInProgress, 
            String blockers, String plannedForTomorrow) {
        
        // Check if tasksCompleted is essentially empty or has placeholder text
        boolean isTasksEmpty = tasksCompleted == null || 
            tasksCompleted.trim().isEmpty() ||
            tasksCompleted.trim().equalsIgnoreCase("nothing") ||
            tasksCompleted.trim().equalsIgnoreCase("none") ||
            tasksCompleted.trim().equalsIgnoreCase("n/a") ||
            tasksCompleted.trim().equalsIgnoreCase("nil") ||
            tasksCompleted.trim().equalsIgnoreCase("-") ||
            tasksCompleted.trim().equalsIgnoreCase(".") ||
            tasksCompleted.trim().length() < 15;

        // Check overall content quality
        int totalContentLength = 0;
        if (tasksCompleted != null) totalContentLength += tasksCompleted.trim().length();
        if (tasksInProgress != null) totalContentLength += tasksInProgress.trim().length();
        if (plannedForTomorrow != null) totalContentLength += plannedForTomorrow.trim().length();

        // COMPLETELY EMPTY REPORT - REJECTED with 0%
        if (totalContentLength < 10) {
            DailyReportGradingResult result = new DailyReportGradingResult();
            result.overallScore = BigDecimal.ZERO;
            result.clarityScore = BigDecimal.ZERO;
            result.productivityScore = BigDecimal.ZERO;
            result.kpiAlignmentScore = BigDecimal.ZERO;
            result.feedback = "⚠️ REPORT REJECTED: This report is essentially empty. You must document the actual work you completed today. Empty reports are not acceptable and will significantly impact your Aura Score.";
            result.strengths = List.of();
            result.improvements = List.of(
                "Actually describe what work you did today",
                "Include specific tasks, meetings, or activities",
                "Document any progress made on your KPIs"
            );
            result.suggestionsForTomorrow = List.of(
                "Keep notes throughout the day of what you work on",
                "Set reminders to document tasks as you complete them",
                "Review your calendar and task list before writing your report"
            );
            return result;
        }

        // Very poor report - barely any meaningful content
        if (isTasksEmpty || totalContentLength < 40) {
            DailyReportGradingResult result = new DailyReportGradingResult();
            result.overallScore = BigDecimal.valueOf(10);
            result.clarityScore = BigDecimal.valueOf(10);
            result.productivityScore = BigDecimal.valueOf(10);
            result.kpiAlignmentScore = BigDecimal.valueOf(15);
            result.feedback = "⚠️ POOR REPORT: This report lacks sufficient detail. Daily reports should describe specific tasks completed with tangible outcomes. Vague or minimal responses will negatively impact your performance score.";
            result.strengths = List.of("Report was submitted on time");
            result.improvements = List.of(
                "Describe specific tasks you worked on with details",
                "Include measurable outcomes and progress percentages",
                "Document challenges and how you addressed them"
            );
            result.suggestionsForTomorrow = List.of(
                "Document your work in detail throughout the day",
                "Note specific accomplishments with metrics where possible",
                "Include how your work aligns with your assigned KPIs"
            );
            return result;
        }

        // Check for reports with all blockers but no work
        boolean hasOnlyBlockers = (tasksCompleted == null || tasksCompleted.trim().length() < 20) &&
            (tasksInProgress == null || tasksInProgress.trim().length() < 10) &&
            (blockers != null && blockers.trim().length() > 20);

        if (hasOnlyBlockers) {
            DailyReportGradingResult result = new DailyReportGradingResult();
            result.overallScore = BigDecimal.valueOf(35);
            result.clarityScore = BigDecimal.valueOf(50);
            result.productivityScore = BigDecimal.valueOf(20);
            result.kpiAlignmentScore = BigDecimal.valueOf(30);
            result.feedback = "While blockers are documented, there should be some work completed or progress made even on challenging days. Consider what tasks you could have advanced despite the obstacles.";
            result.strengths = List.of("Blockers are documented");
            result.improvements = List.of(
                "Work on tasks that don't depend on the blockers",
                "Proactively seek help to resolve blockers faster",
                "Document any partial progress made"
            );
            result.suggestionsForTomorrow = List.of(
                "Escalate blockers early in the day",
                "Have backup tasks ready when blocked"
            );
            return result;
        }

        return null; // Content passes pre-validation, proceed with AI grading
    }

    /**
     * Build prompt for grading daily report
     * AI now provides suggestions instead of scoring planning
     */
    private String buildDailyReportGradingPrompt(
            String employeeName,
            String department,
            String tasksCompleted,
            String tasksInProgress,
            String blockers,
            String plannedForTomorrow,
            String additionalNotes,
            List<String> individualKpis) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict but fair performance evaluation AI for a corporate workplace management system. ");
        sb.append("Grade the following daily work report, provide constructive feedback, ");
        sb.append("suggest priorities for tomorrow, and provide tips to boost their Aura Score.\n\n");
        
        sb.append("EMPLOYEE: ").append(employeeName != null ? employeeName : "Team Member");
        sb.append(" (").append(department != null ? department : "General").append(" department)\n\n");
        
        sb.append("=== DAILY REPORT ===\n\n");
        
        sb.append("TASKS COMPLETED TODAY:\n");
        sb.append(tasksCompleted != null ? tasksCompleted : "Not provided").append("\n\n");
        
        if (tasksInProgress != null && !tasksInProgress.isEmpty()) {
            sb.append("TASKS IN PROGRESS:\n");
            sb.append(tasksInProgress).append("\n\n");
        }
        
        if (blockers != null && !blockers.isEmpty()) {
            sb.append("BLOCKERS/CHALLENGES:\n");
            sb.append(blockers).append("\n\n");
        }
        
        if (plannedForTomorrow != null && !plannedForTomorrow.isEmpty()) {
            sb.append("EMPLOYEE'S PLAN FOR TOMORROW:\n");
            sb.append(plannedForTomorrow).append("\n\n");
        }
        
        if (additionalNotes != null && !additionalNotes.isEmpty()) {
            sb.append("ADDITIONAL NOTES:\n");
            sb.append(additionalNotes).append("\n\n");
        }
        
        if (individualKpis != null && !individualKpis.isEmpty()) {
            sb.append("=== INDIVIDUAL KPIS (Key Performance Indicators) ===\n");
            sb.append("These are the employee's specific goals for this quarter:\n");
            for (String kpi : individualKpis) {
                sb.append("• ").append(kpi).append("\n");
            }
            sb.append("\n");
        }

        sb.append("=== GRADING CRITERIA ===\n");
        sb.append("BE STRICT. Do not give high scores for vague or minimal reports.\n");
        sb.append("- Scores 80+: Excellent detail, clear outcomes, strong KPI alignment\n");
        sb.append("- Scores 60-79: Good but could improve specificity\n");
        sb.append("- Scores 40-59: Needs more detail and concrete outcomes\n");
        sb.append("- Scores 0-39: Poor quality, vague, or insufficient content\n\n");
        
        sb.append("=== YOUR TASK ===\n");
        sb.append("1. GRADE the report (be strict!) based on:\n");
        sb.append("   - CLARITY (30%): Clear, well-written, specific details\n");
        sb.append("   - PRODUCTIVITY (40%): Meaningful work with tangible, measurable outcomes\n");
        sb.append("   - KPI ALIGNMENT (30%): Tasks directly contribute to their assigned KPIs\n\n");
        
        sb.append("2. PROVIDE constructive feedback (2-3 sentences)\n\n");
        
        sb.append("3. SUGGEST 3-4 specific priorities for tomorrow that:\n");
        sb.append("   - Address any blockers mentioned\n");
        sb.append("   - Advance in-progress tasks\n");
        sb.append("   - Directly help achieve their KPIs\n");
        sb.append("   - Are actionable and specific\n\n");
        
        sb.append("4. PROVIDE 2-3 tips to help boost their Aura Score:\n");
        sb.append("   - Aura Score has 4 pillars: Technical (tasks), Behavioral (compliance), Cultural (attendance), Growth (training)\n");
        sb.append("   - Focus on actionable tips based on their report content\n\n");

        sb.append("RESPOND IN THIS EXACT JSON FORMAT (no markdown, no code blocks):\n");
        sb.append("{\n");
        sb.append("  \"overallScore\": <0-100>,\n");
        sb.append("  \"clarityScore\": <0-100>,\n");
        sb.append("  \"productivityScore\": <0-100>,\n");
        sb.append("  \"kpiAlignmentScore\": <0-100>,\n");
        sb.append("  \"feedback\": \"<2-3 sentences of constructive feedback>\",\n");
        sb.append("  \"strengths\": [\"<strength 1>\", \"<strength 2>\"],\n");
        sb.append("  \"improvements\": [\"<area for improvement>\"],\n");
        sb.append("  \"suggestionsForTomorrow\": [\"<specific priority 1>\", \"<specific priority 2>\", \"<specific priority 3>\"],\n");
        sb.append("  \"auraBoostTips\": [\"<tip to boost aura score 1>\", \"<tip 2>\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Parse daily report grading response
     */
    private DailyReportGradingResult parseDailyReportGradingResponse(String aiResponse) {
        DailyReportGradingResult result = new DailyReportGradingResult();

        if (aiResponse == null || aiResponse.isEmpty()) {
            // Default to low score if AI fails - encourage better reports
            result.overallScore = BigDecimal.valueOf(30);
            result.clarityScore = BigDecimal.valueOf(30);
            result.productivityScore = BigDecimal.valueOf(30);
            result.kpiAlignmentScore = BigDecimal.valueOf(30);
            result.feedback = "Report received but could not be analyzed. Please include specific details about completed tasks.";
            result.strengths = List.of("Report was submitted");
            result.improvements = List.of("Include specific task details", "Describe tangible outcomes");
            result.suggestionsForTomorrow = List.of("Document work more thoroughly", "Include measurable progress");
            result.auraBoostTips = List.of("Complete your daily report with detailed descriptions", "Check in on time to improve Cultural Fit pillar");
            return result;
        }

        try {
            // Clean the response
            String cleanResponse = aiResponse.trim();
            // Robust JSON extraction
            int firstOpen = cleanResponse.indexOf('{');
            int lastClose = cleanResponse.lastIndexOf('}');
            if (firstOpen != -1 && lastClose != -1 && lastClose > firstOpen) {
                cleanResponse = cleanResponse.substring(firstOpen, lastClose + 1);
            }

            JsonNode json = objectMapper.readTree(cleanResponse);

            result.overallScore = BigDecimal.valueOf(json.path("overallScore").asDouble(50));
            result.clarityScore = BigDecimal.valueOf(json.path("clarityScore").asDouble(50));
            result.productivityScore = BigDecimal.valueOf(json.path("productivityScore").asDouble(50));
            result.kpiAlignmentScore = BigDecimal.valueOf(json.path("kpiAlignmentScore").asDouble(50));
            result.feedback = json.path("feedback").asText("Report received.");
            result.strengths = jsonArrayToList(json.path("strengths"));
            result.improvements = jsonArrayToList(json.path("improvements"));
            result.suggestionsForTomorrow = jsonArrayToList(json.path("suggestionsForTomorrow"));
            result.auraBoostTips = jsonArrayToList(json.path("auraBoostTips"));
            
            // Fallback if auraBoostTips is empty
            if (result.auraBoostTips == null || result.auraBoostTips.isEmpty()) {
                result.auraBoostTips = List.of(
                    "Complete tasks on time to boost your Technical Competence pillar",
                    "Attend work consistently to improve your Cultural Fit score"
                );
            }

        } catch (Exception e) {
            System.err.println("Error parsing daily report grading response: " + e.getMessage());
            result.overallScore = BigDecimal.valueOf(50);
            result.feedback = "Report received. AI grading temporarily unavailable.";
            result.strengths = List.of();
            result.improvements = List.of();
            result.suggestionsForTomorrow = List.of("Review your individual KPIs", "Focus on high-priority tasks");
            result.auraBoostTips = List.of("Complete all assigned tasks this week", "Submit your daily reports on time");
        }

        return result;
    }

    /**
     * Analyze weekly KPI progress with team member feedback for personalized insights
     */
    public AiAnalysisResult analyzeWeeklyProgressWithFeedback(
            String teamName,
            String department,
            List<KpiProgressData> kpiData,
            List<TeamMemberFeedback> memberFeedback,
            int weekNumber,
            int year) {

        String prompt = buildEnhancedWeeklyAnalysisPrompt(teamName, department, kpiData, memberFeedback, weekNumber, year);
        String aiResponse = callGeminiApi(prompt);
        
        return parseAiResponse(aiResponse, kpiData);
    }

    /**
     * Build enhanced weekly analysis prompt with team member feedback context
     */
    private String buildEnhancedWeeklyAnalysisPrompt(
            String teamName,
            String department,
            List<KpiProgressData> kpiData,
            List<TeamMemberFeedback> memberFeedback,
            int weekNumber,
            int year) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a performance analysis AI for a corporate team management system. ");
        sb.append("You have access to both KPI metrics AND individual team member feedback data. ");
        sb.append("Provide personalized, actionable insights that reference specific employees and their performance.\n\n");
        sb.append("Team: ").append(teamName).append(" (").append(department).append(" department), ");
        sb.append("Week ").append(weekNumber).append(" of ").append(year).append(".\n\n");

        // KPI Progress Section
        sb.append("=== TEAM KPI PROGRESS ===\n");
        for (KpiProgressData kpi : kpiData) {
            sb.append("• ").append(kpi.kpiName).append(": ");
            sb.append(String.format("%.1f", kpi.progressPercentage)).append("% of target ");
            sb.append("(").append(kpi.achievedValue).append("/").append(kpi.targetValue).append(" ").append(kpi.targetUnit).append("), ");
            sb.append("Weight: ").append(kpi.weight).append("%\n");
        }

        // Team Member Feedback Section
        if (!memberFeedback.isEmpty()) {
            sb.append("\n=== INDIVIDUAL TEAM MEMBER PERFORMANCE ===\n");
            for (TeamMemberFeedback member : memberFeedback) {
                sb.append("\n📊 ").append(member.employeeName);
                if (member.role != null) sb.append(" (").append(member.role).append(")");
                sb.append(" - Trend: ").append(member.trend.toUpperCase()).append("\n");
                
                // Ratings (1-5 scale)
                sb.append("  Team Lead Ratings: Initiative=").append(member.initiativeScore);
                sb.append(", Attitude=").append(member.attitudeScore);
                sb.append(", Teamwork=").append(member.teamworkScore);
                sb.append(" (Scale 1-5)\n");
                
                sb.append("  Metric Pillars: Tech=").append(member.technicalScore);
                sb.append(", Behavioral=").append(member.behavioralScore);
                sb.append(", Culture=").append(member.cultureFitScore).append("\n");
                
                if (member.teamReportDocument != null && !member.teamReportDocument.isEmpty()) {
                    sb.append("  [DOCUMENT UPLOADED]: ").append(member.teamReportDocument).append("\n");
                    sb.append("  Wait for feedback specifically referencing the contents of this document.\n");
                }

                if (member.highlights != null && !member.highlights.isEmpty()) {
                    sb.append("  Highlights: ").append(member.highlights).append("\n");
                }
                if (member.areasForFocus != null && !member.areasForFocus.isEmpty()) {
                    sb.append("  Areas for Focus: ").append(member.areasForFocus).append("\n");
                }
            }
        } else {
            sb.append("\n(No individual member feedback data available for this week. Use KPI targets for baseline analysis.)\n");
        }

        sb.append("\n=== ADDITIONAL CONTEXT ===\n");
        sb.append("- The Team Lead has uploaded supplementary documents (e.g., CVs or Work Plans). Explicitly mention these in your summary if relevant to the employee's growth or performance.\n");
        sb.append("- Your goal is to provide HIGHLY DETAILED, STRATEGIC insights. Avoid generic phrases.\n\n");

        sb.append("=== INSTRUCTIONS ===\n");
        sb.append("1. Calculate a KPI score (0-100) based on weighted KPI progress.\n");
        sb.append("2. Provide a 3-4 sentence summary that sounds like a senior management consultant. Reference employees by name and link their soft skill ratings (Initiative/Teamwork) to their actual KPI results.\n");
        sb.append("3. If an uploaded document (e.g., a CV) is mentioned for an employee, provide a personalized recommendation like 'Leverage the skills identified in [Document Name] to bridge the current gap in [Specific KPI]'.\n");
        sb.append("4. TOP PERFORMING: List 2-3 specific achievements using metrics.\n");
        sb.append("5. NEEDS ATTENTION: Focus on behavioral or technical blockers identified in the ratings.\n");
        sb.append("6. RECOMMENDATIONS: Provide 3-4 actionable, high-impact strategies for next week.\n");
        sb.append("7. RISK ALERTS: Identify any 'silent risks' like stable but low engagement scores.\n\n");

        sb.append("RESPOND IN THIS EXACT JSON FORMAT (no markdown, no preamble):\n");
        sb.append("{\n");
        sb.append("  \"kpiScore\": <0-100>,\n");
        sb.append("  \"summary\": \"<Detailed strategic summary>\",\n");
        sb.append("  \"topPerforming\": [\"<Employee>: <Achievement>\"],\n");
        sb.append("  \"needsAttention\": [\"<Employee>: <Root cause of issue>\"],\n");
        sb.append("  \"recommendations\": [\"<Actionable step 1>\", \"<Step 2>\"],\n");
        sb.append("  \"riskAlerts\": [\"<Risk alert 1>\"]\n");
        sb.append("}\n");

        return sb.toString();
    }


    /**
     * Build the weekly analysis prompt
     */
    private String buildWeeklyAnalysisPrompt(
            String teamName,
            String department,
            List<KpiProgressData> kpiData,
            int weekNumber,
            int year) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a performance analysis AI for a corporate team management system. ");
        sb.append("Analyze the following weekly KPI progress for ").append(teamName).append(" (").append(department).append(" department), ");
        sb.append("Week ").append(weekNumber).append(" of ").append(year).append(".\n\n");

        sb.append("KPI PROGRESS DATA:\n");
        sb.append("==================\n");

        for (KpiProgressData kpi : kpiData) {
            sb.append("• ").append(kpi.kpiName).append("\n");
            sb.append("  Target: ").append(kpi.targetValue).append(" ").append(kpi.targetUnit).append("\n");
            sb.append("  Achieved this week: ").append(kpi.achievedValue).append(" ").append(kpi.targetUnit).append("\n");
            sb.append("  Progress: ").append(String.format("%.1f", kpi.progressPercentage)).append("%\n");
            sb.append("  Weight: ").append(kpi.weight).append("%\n");
            if (kpi.notes != null && !kpi.notes.isEmpty()) {
                sb.append("  Notes: ").append(kpi.notes).append("\n");
            }
            sb.append("\n");
        }

        sb.append("\nSCORING RULES:\n");
        sb.append("- 100% or more achieved = 100 points (Exceeded)\n");
        sb.append("- 90-99% achieved = 90 points (Excellent)\n");
        sb.append("- 80-89% achieved = 80 points (Good)\n");
        sb.append("- 70-79% achieved = 70 points (Satisfactory)\n");
        sb.append("- 60-69% achieved = 60 points (Needs Improvement)\n");
        sb.append("- Below 60% achieved = 50 points (At Risk)\n");
        sb.append("- Final Score = Sum of (KPI Score × KPI Weight)\n\n");

        sb.append("RESPOND IN THIS EXACT JSON FORMAT (no markdown, just JSON):\n");
        sb.append("{\n");
        sb.append("  \"kpiScore\": <calculated weighted score 0-100>,\n");
        sb.append("  \"summary\": \"<2-3 sentence summary of performance>\",\n");
        sb.append("  \"topPerforming\": [\"<KPI 1 that exceeded>\", \"<KPI 2 that met target>\"],\n");
        sb.append("  \"needsAttention\": [\"<KPI behind target with brief reason>\"],\n");
        sb.append("  \"recommendations\": [\n");
        sb.append("    \"<Specific actionable recommendation 1>\",\n");
        sb.append("    \"<Specific actionable recommendation 2>\"\n");
        sb.append("  ],\n");
        sb.append("  \"riskAlerts\": [\"<Any critical risks if KPIs continue at current pace>\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Build the quarterly analysis prompt
     */
    private String buildQuarterlyAnalysisPrompt(
            String teamName,
            String department,
            List<KpiProgressData> kpiData,
            String quarter,
            int year) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a performance analysis AI. Generate a quarterly performance review for ");
        sb.append(teamName).append(" (").append(department).append(" department), ");
        sb.append(quarter).append(" ").append(year).append(".\n\n");

        sb.append("QUARTERLY KPI RESULTS:\n");
        sb.append("======================\n");

        for (KpiProgressData kpi : kpiData) {
            sb.append("• ").append(kpi.kpiName).append("\n");
            sb.append("  Target: ").append(kpi.targetValue).append(" ").append(kpi.targetUnit).append("\n");
            sb.append("  Final Achievement: ").append(kpi.achievedValue).append(" ").append(kpi.targetUnit).append("\n");
            sb.append("  Progress: ").append(String.format("%.1f", kpi.progressPercentage)).append("%\n");
            sb.append("  Weight: ").append(kpi.weight).append("%\n\n");
        }

        sb.append("\nRESPOND IN THIS EXACT JSON FORMAT:\n");
        sb.append("{\n");
        sb.append("  \"kpiScore\": <overall quarter score 0-100>,\n");
        sb.append("  \"summary\": \"<Executive summary of quarterly performance>\",\n");
        sb.append("  \"achievements\": [\"<Key achievement 1>\", \"<Key achievement 2>\"],\n");
        sb.append("  \"challenges\": [\"<Challenge faced>\"],\n");
        sb.append("  \"nextQuarterFocus\": [\"<Priority 1 for next quarter>\", \"<Priority 2>\"],\n");
        sb.append("  \"overallAssessment\": \"<Exceeds Expectations/Meets Expectations/Below Expectations>\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Call the Gemini API
     */
    private String callGeminiApi(String prompt) {
        try {
            String url = GEMINI_API_URL + "?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, String>> parts = new ArrayList<>();
            parts.add(Map.of("text", prompt));
            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);

            // Add generation config for better JSON output
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.3);
            generationConfig.put("maxOutputTokens", 1024);
            requestBody.put("generationConfig", generationConfig);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractTextFromResponse(response.getBody());
            }

            System.err.println("Gemini API error: " + response.getStatusCode());
            return null;

        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extract text content from Gemini API response
     */
    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Error parsing Gemini response: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parse AI response into structured result
     */
    private AiAnalysisResult parseAiResponse(String aiResponse, List<KpiProgressData> kpiData) {
        AiAnalysisResult result = new AiAnalysisResult();

        if (aiResponse == null || aiResponse.isEmpty()) {
            // Fallback: calculate score manually
            result.kpiScore = calculateManualScore(kpiData);
            result.summary = "Unable to generate AI insights. Score calculated based on KPI progress.";
            result.insights = Map.of("status", "AI unavailable");
            result.recommendations = Map.of("items", List.of("Review KPI progress manually"));
            result.riskAlerts = Map.of("items", List.of());
            result.rawResponse = Map.of("error", "No AI response");
            return result;
        }

        try {
            // Clean the response (remove markdown if present)
            String cleanResponse = aiResponse.trim();
            // Robust JSON extraction
            int firstOpen = cleanResponse.indexOf('{');
            int lastClose = cleanResponse.lastIndexOf('}');
            if (firstOpen != -1 && lastClose != -1 && lastClose > firstOpen) {
                cleanResponse = cleanResponse.substring(firstOpen, lastClose + 1);
            }
            
            JsonNode json = objectMapper.readTree(cleanResponse);

            // Extract kpiScore
            result.kpiScore = BigDecimal.valueOf(json.path("kpiScore").asDouble(0));

            // Extract summary
            result.summary = json.path("summary").asText("No summary available");

            // Extract insights
            Map<String, Object> insights = new HashMap<>();
            if (json.has("topPerforming")) {
                insights.put("topPerforming", jsonArrayToList(json.path("topPerforming")));
            }
            if (json.has("needsAttention")) {
                insights.put("needsAttention", jsonArrayToList(json.path("needsAttention")));
            }
            if (json.has("achievements")) {
                insights.put("achievements", jsonArrayToList(json.path("achievements")));
            }
            if (json.has("challenges")) {
                insights.put("challenges", jsonArrayToList(json.path("challenges")));
            }
            result.insights = insights;

            // Extract recommendations
            Map<String, Object> recs = new HashMap<>();
            if (json.has("recommendations")) {
                recs.put("items", jsonArrayToList(json.path("recommendations")));
            }
            if (json.has("nextQuarterFocus")) {
                recs.put("nextQuarterFocus", jsonArrayToList(json.path("nextQuarterFocus")));
            }
            result.recommendations = recs;

            // Extract risk alerts
            Map<String, Object> risks = new HashMap<>();
            if (json.has("riskAlerts")) {
                risks.put("items", jsonArrayToList(json.path("riskAlerts")));
            }
            result.riskAlerts = risks;

            // Store raw response
            result.rawResponse = objectMapper.convertValue(json, Map.class);

        } catch (Exception e) {
            System.err.println("Error parsing AI response: " + e.getMessage());
            // Fallback
            result.kpiScore = calculateManualScore(kpiData);
            result.summary = aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse;
            result.insights = Map.of("raw", aiResponse);
            result.recommendations = Map.of("items", List.of());
            result.riskAlerts = Map.of("items", List.of());
            result.rawResponse = Map.of("rawText", aiResponse);
        }

        return result;
    }

    /**
     * Convert JSON array to List
     */
    private List<String> jsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                list.add(node.asText());
            }
        }
        return list;
    }

    /**
     * Calculate score manually if AI fails
     */
    private BigDecimal calculateManualScore(List<KpiProgressData> kpiData) {
        double totalScore = 0;
        double totalWeight = 0;

        for (KpiProgressData kpi : kpiData) {
            double progress = kpi.progressPercentage;
            double kpiScore;

            if (progress >= 100) kpiScore = 100;
            else if (progress >= 90) kpiScore = 90;
            else if (progress >= 80) kpiScore = 80;
            else if (progress >= 70) kpiScore = 70;
            else if (progress >= 60) kpiScore = 60;
            else kpiScore = 50;

            totalScore += kpiScore * (kpi.weight / 100.0);
            totalWeight += kpi.weight;
        }

        // Normalize if weights don't sum to 100
        if (totalWeight > 0 && totalWeight != 100) {
            totalScore = totalScore * (100.0 / totalWeight);
        }

        return BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== DATA CLASSES ====================

    /**
     * Input data for AI analysis
     */
    public static class KpiProgressData {
        public String kpiName;
        public double targetValue;
        public String targetUnit;
        public double achievedValue;
        public double progressPercentage;
        public int weight;
        public String notes;

        public KpiProgressData() {}

        public KpiProgressData(String kpiName, double targetValue, String targetUnit, 
                               double achievedValue, double progressPercentage, int weight, String notes) {
            this.kpiName = kpiName;
            this.targetValue = targetValue;
            this.targetUnit = targetUnit;
            this.achievedValue = achievedValue;
            this.progressPercentage = progressPercentage;
            this.weight = weight;
            this.notes = notes;
        }
    }

    /**
     * Result from AI analysis
     */
    public static class AiAnalysisResult {
        public BigDecimal kpiScore;
        public String summary;
        public Map<String, Object> insights;
        public Map<String, Object> recommendations;
        public Map<String, Object> riskAlerts;
        public Map<String, Object> rawResponse;
    }

    /**
     * Team member feedback data for personalized AI insights
     */
    public static class TeamMemberFeedback {
        public String employeeName;
        public String role;
        public Integer technicalScore;
        public Integer behavioralScore;
        public Integer cultureFitScore;
        public Integer growthScore;
        public Integer teamworkScore;
        public Integer initiativeScore;
        public Integer attitudeScore;
        public String teamReportDocument;
        public String highlights;
        public String areasForFocus;
        public String technicalNotes;
        public String behavioralNotes;
        public String trend; // "improving", "declining", "stable", "new"
        
        public TeamMemberFeedback() {}
    }

    /**
     * Result from daily report grading
     * AI provides score breakdown and actionable suggestions
     */
    public static class DailyReportGradingResult {
        public BigDecimal overallScore;
        public BigDecimal clarityScore;
        public BigDecimal productivityScore;
        public BigDecimal kpiAlignmentScore;
        public String feedback;
        public List<String> strengths;
        public List<String> improvements;
        public List<String> suggestionsForTomorrow; // AI-generated priorities for next day
        public List<String> auraBoostTips; // Tips to boost Aura Score

        public DailyReportGradingResult() {}
    }
}
