package com.schoolable.backend.kpi;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * KPI Controller
 * REST API for KPI management, progress tracking, and AI insights
 */
@RestController
@RequestMapping("/api/kpi")
@CrossOrigin(origins = "*")
public class KpiController {

    @Autowired
    private KpiAnalysisService kpiService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AiInsightRepository insightRepository;

    @Autowired
    private TeamQuarterlyScoreRepository scoreRepository;

    // ==================== KPI MANAGEMENT ====================

    /**
     * GET /api/kpi/my-kpis
     * Get all KPIs for current team lead
     */
    @GetMapping("/my-kpis")
    public ResponseEntity<?> getMyKpis(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year) {

        UUID userId = getUserId(auth);
        if (quarter == null) quarter = kpiService.getCurrentQuarter();
        if (year == null) year = LocalDate.now().getYear();

        List<TeamKpi> kpis = kpiService.getKpisForTeamLead(userId, quarter, year);

        // Calculate remaining weight
        int usedWeight = kpis.stream().mapToInt(TeamKpi::getWeight).sum();

        Map<String, Object> response = new HashMap<>();
        response.put("kpis", kpis);
        response.put("quarter", quarter);
        response.put("year", year);
        response.put("totalWeight", usedWeight);
        response.put("remainingWeight", 100 - usedWeight);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/kpi
     * Create a new KPI
     */
    @PostMapping
    public ResponseEntity<?> createKpi(
            Authentication auth,
            @RequestBody KpiAnalysisService.KpiCreateRequest request) {

        try {
            UUID userId = getUserId(auth);
            TeamKpi kpi = kpiService.createKpi(userId, request);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "KPI created successfully",
                "kpi", kpi
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * PUT /api/kpi/{kpiId}
     * Update a KPI
     */
    @PutMapping("/{kpiId}")
    public ResponseEntity<?> updateKpi(
            Authentication auth,
            @PathVariable UUID kpiId,
            @RequestBody KpiAnalysisService.KpiUpdateRequest request) {

        try {
            UUID userId = getUserId(auth);
            TeamKpi kpi = kpiService.updateKpi(kpiId, userId, request);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "KPI updated successfully",
                "kpi", kpi
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * DELETE /api/kpi/{kpiId}
     * Delete (deactivate) a KPI
     */
    @DeleteMapping("/{kpiId}")
    public ResponseEntity<?> deleteKpi(
            Authentication auth,
            @PathVariable UUID kpiId) {

        try {
            UUID userId = getUserId(auth);
            kpiService.deleteKpi(kpiId, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "KPI deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // ==================== PROGRESS REPORTING ====================

    /**
     * POST /api/kpi/progress
     * Submit weekly progress for KPIs
     */
    @PostMapping("/progress")
    public ResponseEntity<?> submitProgress(
            Authentication auth,
            @RequestBody KpiAnalysisService.WeeklyProgressRequest request) {

        try {
            UUID userId = getUserId(auth);
            
            // Default to current week if not specified
            if (request.weekNumber == null) request.weekNumber = kpiService.getCurrentWeek();
            if (request.year == null) request.year = LocalDate.now().getYear();

            List<WeeklyKpiProgress> progress = kpiService.submitWeeklyProgress(userId, request);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Progress submitted for " + progress.size() + " KPIs",
                "weekNumber", request.weekNumber,
                "progress", progress
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/kpi/progress
     * Get progress for current week
     */
    @GetMapping("/progress")
    public ResponseEntity<?> getProgress(
            Authentication auth,
            @RequestParam(required = false) Integer weekNumber,
            @RequestParam(required = false) Integer year) {

        UUID userId = getUserId(auth);
        if (weekNumber == null) weekNumber = kpiService.getCurrentWeek();
        if (year == null) year = LocalDate.now().getYear();

        List<WeeklyKpiProgress> progress = kpiService.getWeeklyProgress(userId, weekNumber, year);

        return ResponseEntity.ok(Map.of(
            "weekNumber", weekNumber,
            "year", year,
            "progress", progress
        ));
    }

    // ==================== AI INSIGHTS ====================

    /**
     * POST /api/kpi/insights/generate
     * Manually trigger AI insight generation
     */
    @PostMapping("/insights/generate")
    public ResponseEntity<?> generateInsight(
            Authentication auth,
            @RequestParam(required = false) Integer weekNumber,
            @RequestParam(required = false) Integer year) {

        try {
            UUID userId = getUserId(auth);
            if (weekNumber == null) weekNumber = kpiService.getCurrentWeek();
            if (year == null) year = LocalDate.now().getYear();

            AiInsight insight = kpiService.generateWeeklyInsight(userId, weekNumber, year);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI insight generated successfully",
                "insight", formatInsight(insight)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/kpi/insights/latest
     * Get latest AI insight for team
     */
    @GetMapping("/insights/latest")
    public ResponseEntity<?> getLatestInsight(Authentication auth) {
        UUID userId = getUserId(auth);

        return kpiService.getLatestInsight(userId)
            .map(insight -> ResponseEntity.ok(formatInsight(insight)))
            .orElse(ResponseEntity.ok(Map.of(
                "message", "No insights available yet",
                "tip", "Insights are generated automatically every Sunday, or you can trigger one manually"
            )));
    }

    /**
     * GET /api/kpi/insights/team
     * Get insights for team members (by department)
     */
    @GetMapping("/insights/team")
    public ResponseEntity<?> getTeamInsight(Authentication auth) {
        UUID userId = getUserId(auth);
        Profile profile = profileRepository.findById(userId).orElse(null);

        if (profile == null || profile.getDepartment() == null) {
            return ResponseEntity.ok(Map.of("message", "Department not found"));
        }

        return kpiService.getLatestInsightForDepartment(profile.getDepartment())
            .map(insight -> ResponseEntity.ok(formatInsight(insight)))
            .orElse(ResponseEntity.ok(Map.of(
                "message", "No team insights available yet"
            )));
    }

    /**
     * GET /api/kpi/insights/history
     * Get insight history for team
     */
    @GetMapping("/insights/history")
    public ResponseEntity<?> getInsightHistory(Authentication auth) {
        UUID userId = getUserId(auth);
        List<AiInsight> insights = kpiService.getInsightHistory(userId);

        List<Map<String, Object>> formatted = insights.stream()
            .map(this::formatInsight)
            .toList();

        return ResponseEntity.ok(Map.of(
            "insights", formatted,
            "total", formatted.size()
        ));
    }

    // ==================== TEAM SCORES ====================

    /**
     * POST /api/kpi/score/calculate
     * Calculate quarterly team score
     */
    @PostMapping("/score/calculate")
    public ResponseEntity<?> calculateTeamScore(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year) {

        try {
            UUID userId = getUserId(auth);
            if (quarter == null) quarter = kpiService.getCurrentQuarter();
            if (year == null) year = LocalDate.now().getYear();

            TeamQuarterlyScore score = kpiService.calculateQuarterlyScore(userId, quarter, year);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team score calculated",
                "score", formatScore(score)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/kpi/score/my-team
     * Get current team's score
     */
    @GetMapping("/score/my-team")
    public ResponseEntity<?> getMyTeamScore(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year) {

        UUID userId = getUserId(auth);
        if (quarter == null) quarter = kpiService.getCurrentQuarter();
        if (year == null) year = LocalDate.now().getYear();

        return kpiService.getTeamScore(userId, quarter, year)
            .map(score -> ResponseEntity.ok(formatScore(score)))
            .orElse(ResponseEntity.ok(Map.of(
                "message", "No team score available for this quarter"
            )));
    }

    /**
     * GET /api/kpi/score/all-teams
     * Get all team scores (super admin only)
     */
    @GetMapping("/score/all-teams")
    public ResponseEntity<?> getAllTeamScores(
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year) {

        if (quarter == null) quarter = kpiService.getCurrentQuarter();
        if (year == null) year = LocalDate.now().getYear();

        List<TeamQuarterlyScore> scores = kpiService.getAllTeamScores(quarter, year);

        // Calculate stats
        double avgScore = scores.stream()
            .filter(s -> s.getOverallTeamScore() != null)
            .mapToDouble(s -> s.getOverallTeamScore().doubleValue())
            .average()
            .orElse(0);

        List<Map<String, Object>> formattedScores = scores.stream()
            .map(this::formatScore)
            .toList();

        return ResponseEntity.ok(Map.of(
            "quarter", quarter,
            "year", year,
            "teams", formattedScores,
            "totalTeams", scores.size(),
            "averageScore", Math.round(avgScore * 10) / 10.0
        ));
    }

    /**
     * GET /api/kpi/team-kpis
     * Get KPIs for team members (view their department's KPIs)
     */
    @GetMapping("/team-kpis")
    public ResponseEntity<?> getTeamKpis(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year) {

        UUID userId = getUserId(auth);
        Profile profile = profileRepository.findById(userId).orElse(null);

        if (profile == null || profile.getDepartment() == null) {
            return ResponseEntity.ok(Map.of("message", "Department not found"));
        }

        if (quarter == null) quarter = kpiService.getCurrentQuarter();
        if (year == null) year = LocalDate.now().getYear();

        List<TeamKpi> kpis = kpiService.getKpisForDepartment(profile.getDepartment(), quarter, year);

        return ResponseEntity.ok(Map.of(
            "department", profile.getDepartment(),
            "quarter", quarter,
            "year", year,
            "kpis", kpis
        ));
    }

    // ==================== HELPER METHODS ====================

    private UUID getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("Unauthorized");
        }
        return (UUID) auth.getPrincipal();
    }

    private Map<String, Object> formatInsight(AiInsight insight) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("id", insight.getId());
        formatted.put("weekNumber", insight.getWeekNumber());
        formatted.put("quarter", insight.getQuarter());
        formatted.put("year", insight.getYear());
        formatted.put("kpiScore", insight.getKpiScore());
        formatted.put("summary", insight.getSummary());
        formatted.put("insights", insight.getInsights());
        formatted.put("recommendations", insight.getRecommendations());
        formatted.put("riskAlerts", insight.getRiskAlerts());
        formatted.put("generatedAt", insight.getGeneratedAt());
        formatted.put("department", insight.getDepartment());
        return formatted;
    }

    private Map<String, Object> formatScore(TeamQuarterlyScore score) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("id", score.getId());
        formatted.put("teamName", score.getTeamName());
        formatted.put("department", score.getDepartment());
        formatted.put("quarter", score.getQuarter());
        formatted.put("year", score.getYear());
        formatted.put("kpiAchievementScore", score.getKpiAchievementScore());
        formatted.put("overallTeamScore", score.getOverallTeamScore());
        formatted.put("grade", score.getGrade());
        formatted.put("aiSummary", score.getAiSummary());
        return formatted;
    }
}
