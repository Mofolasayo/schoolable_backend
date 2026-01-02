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
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();

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
}
