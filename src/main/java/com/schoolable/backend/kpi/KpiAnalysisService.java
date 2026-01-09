package com.schoolable.backend.kpi;

import com.schoolable.backend.performance.WeeklyPerformanceReport;
import com.schoolable.backend.performance.WeeklyReportRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KPI Analysis Service
 * Orchestrates KPI tracking, AI analysis, and team scoring
 */
@Service
public class KpiAnalysisService {

    @Autowired
    private TeamKpiRepository kpiRepository;

    @Autowired
    private WeeklyKpiProgressRepository progressRepository;

    @Autowired
    private AiInsightRepository insightRepository;

    @Autowired
    private TeamQuarterlyScoreRepository quarterlyScoreRepository;

    @Autowired
    private GeminiAiService geminiService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    // ==================== KPI MANAGEMENT ====================

    /**
     * Create a new KPI for a team
     */
    @Transactional
    public TeamKpi createKpi(UUID teamLeadId, KpiCreateRequest request) {
        Profile teamLead = profileRepository.findById(teamLeadId)
            .orElseThrow(() -> new RuntimeException("Team lead not found"));

        // Validate total weight doesn't exceed 100
        Integer currentWeight = kpiRepository.sumWeightByTeamLeadAndQuarter(
            teamLeadId, request.quarter, request.year);
        if (currentWeight + request.weight > 100) {
            throw new RuntimeException("Total KPI weight cannot exceed 100%. Current: " + currentWeight + "%, Adding: " + request.weight + "%");
        }

        TeamKpi kpi = new TeamKpi();
        kpi.setTeamLeadId(teamLeadId);
        kpi.setDepartment(teamLead.getDepartment());
        kpi.setName(request.name);
        kpi.setDescription(request.description);
        kpi.setTargetValue(BigDecimal.valueOf(request.targetValue));
        kpi.setTargetUnit(request.targetUnit);
        kpi.setWeight(request.weight);
        kpi.setQuarter(request.quarter);
        kpi.setYear(request.year);
        kpi.setIsActive(true);

        return kpiRepository.save(kpi);
    }

    /**
     * Update an existing KPI
     */
    @Transactional
    public TeamKpi updateKpi(UUID kpiId, UUID teamLeadId, KpiUpdateRequest request) {
        TeamKpi kpi = kpiRepository.findById(kpiId)
            .orElseThrow(() -> new RuntimeException("KPI not found"));

        if (!kpi.getTeamLeadId().equals(teamLeadId)) {
            throw new RuntimeException("Not authorized to update this KPI");
        }

        // Check weight if changing
        if (request.weight != null && !request.weight.equals(kpi.getWeight())) {
            Integer currentWeight = kpiRepository.sumWeightByTeamLeadAndQuarter(
                teamLeadId, kpi.getQuarter(), kpi.getYear());
            int newTotal = currentWeight - kpi.getWeight() + request.weight;
            if (newTotal > 100) {
                throw new RuntimeException("Total KPI weight cannot exceed 100%. Would be: " + newTotal + "%");
            }
            kpi.setWeight(request.weight);
        }

        if (request.name != null) kpi.setName(request.name);
        if (request.description != null) kpi.setDescription(request.description);
        if (request.targetValue != null) kpi.setTargetValue(BigDecimal.valueOf(request.targetValue));
        if (request.targetUnit != null) kpi.setTargetUnit(request.targetUnit);
        if (request.isActive != null) kpi.setIsActive(request.isActive);

        return kpiRepository.save(kpi);
    }

    /**
     * Delete (deactivate) a KPI
     */
    @Transactional
    public void deleteKpi(UUID kpiId, UUID teamLeadId) {
        TeamKpi kpi = kpiRepository.findById(kpiId)
            .orElseThrow(() -> new RuntimeException("KPI not found"));

        if (!kpi.getTeamLeadId().equals(teamLeadId)) {
            throw new RuntimeException("Not authorized to delete this KPI");
        }

        kpi.setIsActive(false);
        kpiRepository.save(kpi);
    }

    /**
     * Get all KPIs for a team lead
     */
    public List<TeamKpi> getKpisForTeamLead(UUID teamLeadId, String quarter, Integer year) {
        return kpiRepository.findByTeamLeadIdAndQuarterAndYearAndIsActiveTrue(teamLeadId, quarter, year);
    }

    /**
     * Get KPIs for a department (for team members)
     */
    public List<TeamKpi> getKpisForDepartment(String department, String quarter, Integer year) {
        return kpiRepository.findByDepartmentAndQuarterAndYearAndIsActiveTrue(department, quarter, year);
    }

    // ==================== WEEKLY PROGRESS REPORTING ====================

    /**
     * Submit weekly progress for KPIs
     */
    @Transactional
    public List<WeeklyKpiProgress> submitWeeklyProgress(UUID teamLeadId, WeeklyProgressRequest request) {
        List<WeeklyKpiProgress> savedProgress = new ArrayList<>();

        for (KpiProgressItem item : request.progress) {
            TeamKpi kpi = kpiRepository.findById(item.kpiId)
                .orElseThrow(() -> new RuntimeException("KPI not found: " + item.kpiId));

            if (!kpi.getTeamLeadId().equals(teamLeadId)) {
                throw new RuntimeException("Not authorized to report on this KPI");
            }

            // Check if already reported this week
            Optional<WeeklyKpiProgress> existing = progressRepository
                .findByKpiIdAndWeekNumberAndYear(item.kpiId, request.weekNumber, request.year);

            WeeklyKpiProgress progress;
            if (existing.isPresent()) {
                progress = existing.get();
                progress.setAchievedValue(BigDecimal.valueOf(item.achievedValue));
                progress.setNotes(item.notes);
            } else {
                progress = new WeeklyKpiProgress();
                progress.setKpiId(item.kpiId);
                progress.setReportedBy(teamLeadId);
                progress.setWeekNumber(request.weekNumber);
                progress.setYear(request.year);
                progress.setAchievedValue(BigDecimal.valueOf(item.achievedValue));
                progress.setNotes(item.notes);
            }

            // Calculate progress percentage
            double progressPct = (item.achievedValue / kpi.getTargetValue().doubleValue()) * 100;
            progress.setProgressPercentage(BigDecimal.valueOf(progressPct).setScale(2, RoundingMode.HALF_UP));

            savedProgress.add(progressRepository.save(progress));
        }

        return savedProgress;
    }

    /**
     * Get weekly progress for a team
     */
    public List<WeeklyKpiProgress> getWeeklyProgress(UUID teamLeadId, Integer weekNumber, Integer year) {
        return progressRepository.findByReportedByAndWeekNumberAndYear(teamLeadId, weekNumber, year);
    }

    // ==================== AI ANALYSIS ====================

    /**
     * Trigger AI analysis for a specific week - now with team member feedback context
     */
    @Transactional
    public AiInsight generateWeeklyInsight(UUID teamLeadId, Integer weekNumber, Integer year) {
        Profile teamLead = profileRepository.findById(teamLeadId)
            .orElseThrow(() -> new RuntimeException("Team lead not found"));

        String quarter = getQuarterForWeek(weekNumber);
        List<TeamKpi> kpis = getKpisForTeamLead(teamLeadId, quarter, year);

        if (kpis.isEmpty()) {
            throw new RuntimeException("No KPIs defined for this quarter");
        }

        // Build KPI progress data for AI
        List<GeminiAiService.KpiProgressData> kpiData = new ArrayList<>();
        for (TeamKpi kpi : kpis) {
            // Get cumulative progress for the quarter
            Double cumulative = progressRepository.sumAchievedValueByKpiIdAndYear(kpi.getId(), year);
            double achieved = cumulative != null ? cumulative : 0;
            double progressPct = (achieved / kpi.getTargetValue().doubleValue()) * 100;

            GeminiAiService.KpiProgressData data = new GeminiAiService.KpiProgressData(
                kpi.getName(),
                kpi.getTargetValue().doubleValue(),
                kpi.getTargetUnit() != null ? kpi.getTargetUnit() : "units",
                achieved,
                progressPct,
                kpi.getWeight(),
                null
            );
            kpiData.add(data);
        }

        // NEW: Collect team member feedback data from weekly reports for personalized insights
        List<GeminiAiService.TeamMemberFeedback> memberFeedback = new ArrayList<>();
        
        // Get team members in the same department
        List<Profile> teamMembers = profileRepository.findByDepartment(teamLead.getDepartment())
                .stream()
                .filter(p -> !p.getId().equals(teamLeadId))
                .filter(p -> {
                    String status = p.getStatus();
                    return status == null || status.isEmpty() || 
                           "active".equalsIgnoreCase(status) || 
                           "pending".equalsIgnoreCase(status) ||
                           "probation".equalsIgnoreCase(status);
                })
                .collect(Collectors.toList());

        // Fetch weekly reports for team members for the specified week
        for (Profile member : teamMembers) {
            Optional<WeeklyPerformanceReport> reportOpt = weeklyReportRepository
                    .findByEmployeeIdAndWeekNumberAndYear(member.getId(), weekNumber, year);
            
            if (reportOpt.isPresent()) {
                WeeklyPerformanceReport report = reportOpt.get();
                
                GeminiAiService.TeamMemberFeedback feedback = new GeminiAiService.TeamMemberFeedback();
                feedback.employeeName = member.getFullName();
                feedback.role = member.getJobTitle();
                feedback.technicalScore = report.getTechnicalScore();
                feedback.behavioralScore = report.getBehavioralScore();
                feedback.cultureFitScore = report.getCultureFitScore();
                feedback.growthScore = report.getGrowthLearningScore();
                feedback.initiativeScore = report.getInitiativeScore();
                feedback.attitudeScore = report.getAttitudeTowardsWorkScore();
                feedback.teamworkScore = report.getTeamworkCollaborationScore();
                feedback.teamReportDocument = report.getTeamReportUrl();
                feedback.highlights = report.getWeeklyHighlights();
                feedback.areasForFocus = report.getAreasForFocus();
                feedback.technicalNotes = report.getTechnicalNotes();
                feedback.behavioralNotes = report.getBehavioralNotes();
                
                // Also get previous week data for trend analysis
                Optional<WeeklyPerformanceReport> prevReportOpt = weeklyReportRepository
                        .findByEmployeeIdAndWeekNumberAndYear(member.getId(), weekNumber - 1, year);
                if (prevReportOpt.isPresent()) {
                    WeeklyPerformanceReport prevReport = prevReportOpt.get();
                    int avgThisWeek = (report.getTechnicalScore() + report.getBehavioralScore() + 
                                       report.getCultureFitScore() + report.getGrowthLearningScore()) / 4;
                    int avgPrevWeek = (prevReport.getTechnicalScore() + prevReport.getBehavioralScore() + 
                                       prevReport.getCultureFitScore() + prevReport.getGrowthLearningScore()) / 4;
                    feedback.trend = avgThisWeek > avgPrevWeek ? "improving" : 
                                     avgThisWeek < avgPrevWeek ? "declining" : "stable";
                } else {
                    feedback.trend = "new";
                }
                
                memberFeedback.add(feedback);
            }
        }

        // Call AI service with enhanced context
        GeminiAiService.AiAnalysisResult aiResult = geminiService.analyzeWeeklyProgressWithFeedback(
            teamLead.getFullName() + "'s Team",
            teamLead.getDepartment(),
            kpiData,
            memberFeedback,
            weekNumber,
            year
        );

        // Save insight
        AiInsight insight = new AiInsight();
        insight.setTeamLeadId(teamLeadId);
        insight.setDepartment(teamLead.getDepartment());
        insight.setInsightType("WEEKLY");
        insight.setWeekNumber(weekNumber);
        insight.setQuarter(quarter);
        insight.setYear(year);
        insight.setKpiScore(aiResult.kpiScore);
        insight.setSummary(aiResult.summary);
        insight.setInsights(aiResult.insights);
        insight.setRecommendations(aiResult.recommendations);
        insight.setRiskAlerts(aiResult.riskAlerts);
        insight.setRawAiResponse(aiResult.rawResponse);

        return insightRepository.save(insight);
    }

    /**
     * Scheduled job: Generate insights for all teams every Sunday at 11 PM
     */
    @Scheduled(cron = "0 0 23 * * SUN")
    @Transactional
    public void generateAllWeeklyInsights() {
        System.out.println("🤖 Starting weekly AI insight generation...");

        LocalDate now = LocalDate.now();
        int weekNumber = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        int year = now.getYear();

        // Find all team leads with active KPIs
        String quarter = getQuarterForWeek(weekNumber);
        List<TeamKpi> allKpis = kpiRepository.findAllActiveByQuarterAndYear(quarter, year);

        Set<UUID> teamLeadIds = allKpis.stream()
            .map(TeamKpi::getTeamLeadId)
            .collect(Collectors.toSet());

        int success = 0;
        int errors = 0;

        for (UUID teamLeadId : teamLeadIds) {
            try {
                generateWeeklyInsight(teamLeadId, weekNumber, year);
                success++;
            } catch (Exception e) {
                System.err.println("Error generating insight for " + teamLeadId + ": " + e.getMessage());
                errors++;
            }
        }

        System.out.println("✅ Weekly insight generation complete. Success: " + success + ", Errors: " + errors);
    }

    /**
     * Get latest insight for a team
     */
    public Optional<AiInsight> getLatestInsight(UUID teamLeadId) {
        return insightRepository.findFirstByTeamLeadIdAndInsightTypeOrderByGeneratedAtDesc(teamLeadId, "WEEKLY");
    }

    /**
     * Get latest insight for a department (for team members)
     */
    public Optional<AiInsight> getLatestInsightForDepartment(String department) {
        return insightRepository.findFirstByDepartmentAndInsightTypeOrderByGeneratedAtDesc(department, "WEEKLY");
    }

    /**
     * Get insight history for a team
     */
    public List<AiInsight> getInsightHistory(UUID teamLeadId) {
        return insightRepository.findByTeamLeadIdOrderByGeneratedAtDesc(teamLeadId);
    }

    // ==================== QUARTERLY SCORING ====================

    /**
     * Calculate and save quarterly team score
     */
    @Transactional
    public TeamQuarterlyScore calculateQuarterlyScore(UUID teamLeadId, String quarter, Integer year) {
        Profile teamLead = profileRepository.findById(teamLeadId)
            .orElseThrow(() -> new RuntimeException("Team lead not found"));

        List<TeamKpi> kpis = getKpisForTeamLead(teamLeadId, quarter, year);

        if (kpis.isEmpty()) {
            throw new RuntimeException("No KPIs defined for this quarter");
        }

        // Build KPI progress data
        List<GeminiAiService.KpiProgressData> kpiData = new ArrayList<>();
        for (TeamKpi kpi : kpis) {
            Double cumulative = progressRepository.sumAchievedValueByKpiIdAndYear(kpi.getId(), year);
            double achieved = cumulative != null ? cumulative : 0;
            double progressPct = (achieved / kpi.getTargetValue().doubleValue()) * 100;

            kpiData.add(new GeminiAiService.KpiProgressData(
                kpi.getName(),
                kpi.getTargetValue().doubleValue(),
                kpi.getTargetUnit(),
                achieved,
                progressPct,
                kpi.getWeight(),
                null
            ));
        }

        // Get AI analysis
        GeminiAiService.AiAnalysisResult aiResult = geminiService.analyzeQuarterlyPerformance(
            teamLead.getFullName() + "'s Team",
            teamLead.getDepartment(),
            kpiData,
            quarter,
            year
        );

        // Find or create quarterly score
        TeamQuarterlyScore score = quarterlyScoreRepository
            .findByTeamLeadIdAndQuarterAndYear(teamLeadId, quarter, year)
            .orElse(new TeamQuarterlyScore(teamLeadId, quarter, year));

        score.setDepartment(teamLead.getDepartment());
        score.setTeamName(teamLead.getFullName() + "'s Team");
        score.setKpiAchievementScore(aiResult.kpiScore);
        score.setOverallTeamScore(aiResult.kpiScore); // Can add more factors later
        score.setAiSummary(aiResult.summary);
        score.calculateGrade();

        return quarterlyScoreRepository.save(score);
    }

    /**
     * Get all team scores for a quarter (super admin view)
     */
    public List<TeamQuarterlyScore> getAllTeamScores(String quarter, Integer year) {
        return quarterlyScoreRepository.findByQuarterAndYearOrderByOverallTeamScoreDesc(quarter, year);
    }

    /**
     * Get team score for current user's team
     */
    public Optional<TeamQuarterlyScore> getTeamScore(UUID teamLeadId, String quarter, Integer year) {
        return quarterlyScoreRepository.findByTeamLeadIdAndQuarterAndYear(teamLeadId, quarter, year);
    }

    // ==================== HELPER METHODS ====================

    private String getQuarterForWeek(int weekNumber) {
        if (weekNumber <= 13) return "Q1";
        if (weekNumber <= 26) return "Q2";
        if (weekNumber <= 39) return "Q3";
        return "Q4";
    }

    public String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    public int getCurrentWeek() {
        return LocalDate.now().get(WeekFields.of(Locale.getDefault()).weekOfYear());
    }

    // ==================== REQUEST DTOs ====================

    public static class KpiCreateRequest {
        public String name;
        public String description;
        public Double targetValue;
        public String targetUnit;
        public Integer weight;
        public String quarter;
        public Integer year;
    }

    public static class KpiUpdateRequest {
        public String name;
        public String description;
        public Double targetValue;
        public String targetUnit;
        public Integer weight;
        public Boolean isActive;
    }

    public static class WeeklyProgressRequest {
        public Integer weekNumber;
        public Integer year;
        public List<KpiProgressItem> progress;
    }

    public static class KpiProgressItem {
        public UUID kpiId;
        public Double achievedValue;
        public String notes;
    }
}
