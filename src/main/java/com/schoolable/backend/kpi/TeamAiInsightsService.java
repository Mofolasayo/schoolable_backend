package com.schoolable.backend.kpi;

import com.schoolable.backend.performance.AuraDashboardDto.EmployeeAuraResponse;
import com.schoolable.backend.performance.AuraDashboardService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Team AI Insights Service
 * Generates aggregated performance insights for entire teams using Gemini AI.
 */
@Service
public class TeamAiInsightsService {

    @Autowired
    private GeminiAiService geminiService;

    @Autowired
    private ProfileRepository profileRepository;
    
    @Autowired
    private AuraDashboardService auraDashboardService;

    @Autowired
    private WeeklyKpiContextService weeklyKpiContextService;

    public Map<String, Object> generateTeamInsights(UUID teamLeadId) {
        Profile teamLead = profileRepository.findById(teamLeadId).orElse(null);
        if (teamLead == null) {
            return Map.of("error", "Team Lead not found");
        }

        String department = teamLead.getDepartment();
        if (department == null) {
             return Map.of("error", "Team Lead has no department");
        }
        
        List<Profile> teamMembers = profileRepository.findByDepartment(department)
                .stream()
                .filter(p -> "active".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());

        LocalDate now = LocalDate.now();
        int weekNumber = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        int year = now.getYear();

        // Gather Team Context
        Map<String, Object> teamContext = gatherTeamContext(teamLeadId, department, teamMembers, weekNumber, year);

        // Generate AI Analysis
        GeminiAiService.StructuredResult aiResponse = generateAiTeamInsights(department, teamContext, weekNumber, year);

        // Return
        return parseTeamInsights(aiResponse, teamContext, teamLead);
    }
    
    private Map<String, Object> gatherTeamContext(UUID teamLeadId, String department, List<Profile> members, int weekNumber, int year) {
        Map<String, Object> ctx = new HashMap<>();
        
        // 1. Team Composition
        ctx.put("teamSize", members.size());
        ctx.put("department", department);
        
        // 2. Aggregated Aura Scores (Performance Distribution)
        List<Double> auraScores = new ArrayList<>();
        double totalAura = 0;
        for (Profile p : members) {
             try {
                EmployeeAuraResponse aura = auraDashboardService.getEmployeeAuraDashboard(p.getId());
                if (aura != null && aura.getAuraScore() != null) {
                    auraScores.add(aura.getAuraScore());
                    totalAura += aura.getAuraScore();
                }
             } catch (Exception e) {}
        }
        ctx.put("avgAuraScore", auraScores.isEmpty() ? 0 : totalAura / auraScores.size());
        ctx.put("lowPerformersCount", auraScores.stream().filter(s -> s < 3.0).count());
        ctx.put("highPerformersCount", auraScores.stream().filter(s -> s >= 4.0).count());

        // 3. Task Throughput & Health
        // In a real scenario, we'd query by department, but here we scan members
        long totalCompleted = 0;
        long totalPending = 0;
        long totalOverdue = 0;
        
        // This is expensive in a loop, but MVP acceptable
        for (Profile p : members) {
             // simplify: just counting 'raw' totals from repo methods if available
             // For now, let's assuming we fetch all tasks for department efficiently
        }
        // Mocking aggregated task stats for the prompt structure (to avoid 20+ DB calls)
        WeeklyKpiContext contextSnapshot = weeklyKpiContextService
            .getOrBuildTeamContext(teamLeadId, weekNumber, year)
            .orElse(null);
        if (contextSnapshot != null) {
            ctx.put("contextText", contextSnapshot.getContextText());
            ctx.put("contextJson", contextSnapshot.getContextJson());
        }

        return ctx;
    }

    private GeminiAiService.StructuredResult generateAiTeamInsights(String department, Map<String, Object> data, int weekNumber, int year) {
        Map<String, Object> contextJson = mapValue(data.get("contextJson"));
        Map<String, Object> taskSummary = mapValue(contextJson.get("tasks"));
        Map<String, Object> attendanceSummary = mapValue(contextJson.get("attendance"));
        Map<String, Object> kpiSummary = mapValue(contextJson.get("teamKpis"));
        Map<String, Object> auraSummary = mapValue(contextJson.get("aura"));

        double taskCompletion = percent(taskSummary.get("completed"), taskSummary.get("total"));
        double onTimeRate = numberValue(taskSummary.get("onTimeRate"));
        double attendanceRate = numberValue(attendanceSummary.get("attendanceRate"));
        double avgAura = numberValue(auraSummary.get("averageAuraScore"), numberValue(data.get("avgAuraScore")));
        double weightedKpiProgress = computeWeightedKpiProgress(kpiSummary);
        long weeklyReportsSubmitted = longValue(contextJson.get("weeklyReportsSubmitted"));

        String contextText = stringValue(data.get("contextText"));

        String prompt = String.format("""
            You are a Strategy Consultant analyzing a team's weekly performance.

            TEAM: %s
            WEEK: %d of %d

            TEAM SNAPSHOT:
            %s

            METRICS:
            - Team size: %d
            - Weekly reports submitted: %d
            - KPI weighted progress: %.1f%%
            - Task completion rate: %.1f%%
            - On-time delivery: %.1f%%
            - Attendance rate: %.1f%%
            - Avg Aura (0-5): %.2f
            - High performers (Aura >= 4.0): %d
            - Needs attention (Aura < 3.0): %d

            Provide a Strategic Team Assessment in JSON:
            {
               "executiveSummary": "High-level health check of the team.",
               "keyWins": ["win1", "win2"],
               "riskFlags": ["risk1", "risk2"],
               "strategicRecommendations": ["rec1", "rec2"]
            }
            """,
            department,
            weekNumber,
            year,
            contextText != null ? contextText : "No team snapshot available.",
            intValue(data.get("teamSize")),
            weeklyReportsSubmitted,
            weightedKpiProgress,
            taskCompletion,
            onTimeRate,
            attendanceRate,
            avgAura,
            longValue(data.get("highPerformersCount")),
            longValue(data.get("lowPerformersCount"))
        );

        return geminiService.generateStructuredInsight(
            prompt,
            teamInsightSchema(),
            "team-insights-v2",
            null
        );
    }

    private Map<String, Object> parseTeamInsights(GeminiAiService.StructuredResult aiResponse, Map<String, Object> data, Profile teamLead) {
        Map<String, Object> res = new HashMap<>(data);
        res.put("teamLeadId", teamLead.getId());
        res.put("teamLeadName", teamLead.getFullName());
        res.put("generatedAt", java.time.OffsetDateTime.now());
        res.put("promptVersion", aiResponse != null ? aiResponse.promptVersion : null);
        res.put("modelUsed", aiResponse != null ? aiResponse.modelUsed : null);
        res.put("aiRequestId", aiResponse != null ? aiResponse.requestId : null);
        res.put("cacheHit", aiResponse != null && aiResponse.cacheHit);

        if (aiResponse != null && aiResponse.data != null) {
            res.put("aiInsights", aiResponse.data);
        } else {
            res.put("aiInsights", Map.of(
                "executiveSummary", "Team performance data collected - AI analysis pending.",
                "keyWins", List.of(),
                "riskFlags", List.of("AI analysis unavailable."),
                "strategicRecommendations", List.of(
                    "Review weekly reports to unlock detailed insights.",
                    "Ensure KPI targets are updated and measurable."
                )
            ));
            res.put("aiError", aiResponse != null ? aiResponse.error : "AI response unavailable");
        }

        return res;
    }

    private Map<String, Object> teamInsightSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "executiveSummary", Map.of("type", "string"),
                "keyWins", Map.of("type", "array", "items", Map.of("type", "string")),
                "riskFlags", Map.of("type", "array", "items", Map.of("type", "string")),
                "strategicRecommendations", Map.of("type", "array", "items", Map.of("type", "string"))
            ),
            "required", List.of("executiveSummary", "keyWins", "riskFlags", "strategicRecommendations"),
            "additionalProperties", false
        );
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    typed.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return typed;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private double numberValue(Object value) {
        return numberValue(value, 0.0);
    }

    private double numberValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double percent(Object numerator, Object denominator) {
        double num = numberValue(numerator);
        double denom = numberValue(denominator);
        if (denom <= 0) {
            return 0.0;
        }
        return Math.round((num / denom * 100.0) * 10.0) / 10.0;
    }

    private double computeWeightedKpiProgress(Map<String, Object> kpiSummary) {
        Object itemsObj = kpiSummary.get("items");
        if (!(itemsObj instanceof List<?> items)) {
            return 0.0;
        }

        double weighted = 0.0;
        double totalWeight = 0.0;
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            Object weightObj = item.get("weight");
            Object progressObj = item.get("progressPct");
            double weight = numberValue(weightObj);
            double progress = Math.max(0.0, Math.min(100.0, numberValue(progressObj)));
            if (weight <= 0) {
                continue;
            }
            weighted += progress * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return 0.0;
        }
        return Math.round((weighted / totalWeight) * 10.0) / 10.0;
    }
}
