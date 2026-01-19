package com.schoolable.backend.kpi;

import com.schoolable.backend.ai.AiJob;
import com.schoolable.backend.ai.AiJobService;
import com.schoolable.backend.ai.AiJobTypes;
import com.schoolable.backend.hr.TeamLeadAppointment;
import com.schoolable.backend.hr.TeamLeadRepository;
import com.schoolable.backend.performance.WeeklyPerformanceReport;
import com.schoolable.backend.performance.WeeklyReportRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KPI Analysis Service
 * Orchestrates KPI tracking, AI analysis, and team scoring
 */
@Service
public class KpiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(KpiAnalysisService.class);

    @Autowired
    private TeamKpiRepository kpiRepository;

    @Autowired
    private IndividualKpiRepository individualKpiRepository;

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

    @Autowired
    private AiJobService aiJobService;

    @Autowired
    private TeamLeadRepository teamLeadRepository;

    @Autowired
    private TeamReportDocumentService teamReportDocumentService;

    @Autowired
    private WeeklyKpiContextService weeklyKpiContextService;

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
        String progressSource = request.progressSource != null && !request.progressSource.isBlank()
            ? request.progressSource
            : "DAILY_REPORT_KPI_ALIGNMENT";
        kpi.setProgressSource(progressSource);
        kpi.setProgressConfig(request.progressConfig);
        boolean autoProgressEnabled = request.autoProgressEnabled != null
            ? request.autoProgressEnabled
            : (progressSource != null && !progressSource.isBlank());
        kpi.setAutoProgressEnabled(autoProgressEnabled);

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

        boolean changed = false;
        if (request.name != null) { kpi.setName(request.name); changed = true; }
        if (request.description != null) { kpi.setDescription(request.description); changed = true; }
        if (request.targetValue != null) { kpi.setTargetValue(BigDecimal.valueOf(request.targetValue)); changed = true; }
        if (request.targetUnit != null) { kpi.setTargetUnit(request.targetUnit); changed = true; }
        if (request.isActive != null) { kpi.setIsActive(request.isActive); changed = true; }
        if (request.progressSource != null) {
            kpi.setProgressSource(request.progressSource);
            if (request.autoProgressEnabled == null) {
                kpi.setAutoProgressEnabled(true);
            }
            changed = true;
        }
        if (request.progressConfig != null) { kpi.setProgressConfig(request.progressConfig); changed = true; }
        if (request.autoProgressEnabled != null) { kpi.setAutoProgressEnabled(request.autoProgressEnabled); changed = true; }

        if (changed) {
            kpi.setVersion(kpi.getVersion() != null ? kpi.getVersion() + 1 : 1);
        }

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
            progress.setSource("manual");
            progress.setIngestedAt(OffsetDateTime.now());

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
        return generateWeeklyInsight(teamLeadId, weekNumber, year, null, null);
    }

    @Transactional
    public AiInsight generateWeeklyInsight(UUID teamLeadId, Integer weekNumber, Integer year, UUID jobId, UUID requestedBy) {
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
        BigDecimal kpiProgressScore = calculateWeightedProgressScore(kpiData);

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

        // Call AI service with enhanced context (or strict fallback if no reports)
        String teamName = resolveTeamName(teamLead);
        GeminiAiService.AiAnalysisResult aiResult;
        String contextText = weeklyKpiContextService.getOrBuildTeamContext(teamLeadId, weekNumber, year)
            .map(WeeklyKpiContext::getContextText)
            .orElse(null);
        if (memberFeedback.isEmpty()) {
            aiResult = new GeminiAiService.AiAnalysisResult();
            aiResult.fallback = true;
            aiResult.kpiScore = BigDecimal.ZERO;
            aiResult.summary = String.format(
                "No weekly reports were submitted for Week %d. Team score is 0 until reports are submitted.",
                weekNumber
            );
            aiResult.insights = Map.of(
                "topPerforming", List.of(),
                "needsAttention", List.of("No weekly reports submitted for this week."),
                "achievements", List.of(),
                "challenges", List.of("Weekly report coverage is 0%.")
            );
            aiResult.recommendations = Map.of("items", List.of(
                "Submit weekly reports for every team member before generating insights.",
                "Include highlights and focus areas to improve KPI guidance.",
                "Upload the team summary document to provide context."
            ));
            aiResult.riskAlerts = Map.of("items", List.of(
                "No weekly reports submitted; team score set to 0."
            ));
            aiResult.rawResponse = Map.of("error", "NO_WEEKLY_REPORTS");
            attachScoreBreakdown(aiResult, kpiProgressScore, false, "NO_WEEKLY_REPORTS");
        } else {
            String teamReportUrl = memberFeedback.stream()
                .map(feedback -> feedback.teamReportDocument)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);

            String teamReportText = null;
            if (teamReportUrl != null) {
                teamReportText = teamReportDocumentService.extractReportText(teamReportUrl).orElse(null);
            }

            boolean reportAvailable = teamReportText != null && !teamReportText.isBlank();
            if (teamReportUrl != null && !reportAvailable) {
                aiResult = new GeminiAiService.AiAnalysisResult();
                aiResult.fallback = true;
                aiResult.kpiScore = kpiProgressScore;
                aiResult.summary = "Team report document could not be accessed. Showing KPI progress baseline only.";
                aiResult.insights = Map.of(
                    "topPerforming", List.of(),
                    "needsAttention", List.of("Team report document could not be accessed."),
                    "achievements", List.of(),
                    "challenges", List.of("Report document is required for AI analysis.")
                );
                aiResult.recommendations = Map.of("items", List.of(
                    "Re-upload the team report document and confirm it opens in the browser.",
                    "Generate insights again after the document is accessible."
                ));
                aiResult.riskAlerts = Map.of("items", List.of(
                    "Weekly report document unavailable; AI insights not generated."
                ));
                Map<String, Object> rawResponse = new HashMap<>();
                rawResponse.put("error", "REPORT_DOCUMENT_UNAVAILABLE");
                rawResponse.put("teamReportUrl", teamReportUrl);
                aiResult.rawResponse = rawResponse;
                attachScoreBreakdown(aiResult, kpiProgressScore, false, "REPORT_BLOCKED");
            } else {
                aiResult = geminiService.analyzeWeeklyProgressWithFeedback(
                    teamName,
                    teamLead.getDepartment(),
                    kpiData,
                    memberFeedback,
                    teamReportText,
                    contextText,
                    weekNumber,
                    year,
                    jobId
                );
                attachScoreBreakdown(aiResult, kpiProgressScore, reportAvailable, reportAvailable ? "AI_ANALYSIS" : "AI_NO_REPORT");
            }
        }

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
        insight.setPromptVersion(aiResult.promptVersion);
        insight.setModelUsed(aiResult.modelUsed);
        insight.setAiRequestId(aiResult.requestId);
        insight.setAiJobId(jobId);
        insight.setGeneratedBy(requestedBy);
        insight.setGenerationStatus(aiResult.fallback ? "FALLBACK" : "COMPLETED");

        return insightRepository.save(insight);
    }

    public AiJob enqueueWeeklyInsight(UUID teamLeadId, Integer weekNumber, Integer year, UUID requestedBy) {
        return aiJobService.enqueueJob(
            AiJobTypes.KPI_WEEKLY_INSIGHT,
            Map.of(
                "teamLeadId", teamLeadId.toString(),
                "weekNumber", weekNumber,
                "year", year,
                "requestedBy", requestedBy != null ? requestedBy.toString() : null
            ),
            3
        );
    }

    @Transactional
    public void processWeeklyInsightJob(UUID jobId, Map<String, Object> payload) {
        UUID teamLeadId = UUID.fromString(payload.get("teamLeadId").toString());
        Integer weekNumber = Integer.valueOf(payload.get("weekNumber").toString());
        Integer year = Integer.valueOf(payload.get("year").toString());
        UUID requestedBy = payload.get("requestedBy") != null ? UUID.fromString(payload.get("requestedBy").toString()) : null;

        generateWeeklyInsight(teamLeadId, weekNumber, year, jobId, requestedBy);
    }

    /**
     * Scheduled job: Generate insights for all teams every Sunday at 11 PM
     */
    @Scheduled(cron = "0 0 23 * * SUN")
    @Transactional
    public void generateAllWeeklyInsights() {
        log.info("Starting weekly AI insight generation");

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
                enqueueWeeklyInsight(teamLeadId, weekNumber, year, null);
                success++;
            } catch (Exception e) {
                log.warn("Error generating insight for {}: {}", teamLeadId, e.getMessage());
                errors++;
            }
        }

        log.info("Weekly insight generation complete. Success: {}, Errors: {}", success, errors);
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
        return calculateQuarterlyScore(teamLeadId, quarter, year, null, null);
    }

    @Transactional
    public TeamQuarterlyScore calculateQuarterlyScore(UUID teamLeadId, String quarter, Integer year, UUID jobId, UUID requestedBy) {
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

        // Get AI analysis (skip if there is no KPI progress submitted for the quarter)
        String teamName = resolveTeamName(teamLead);
        boolean hasProgress = !progressRepository.findAllByTeamLeadAndQuarter(teamLeadId, quarter, year).isEmpty();
        GeminiAiService.AiAnalysisResult aiResult;
        if (!hasProgress) {
            aiResult = new GeminiAiService.AiAnalysisResult();
            aiResult.fallback = true;
            aiResult.kpiScore = BigDecimal.ZERO;
            aiResult.summary = String.format(
                "No KPI progress has been reported for %s %d. Team score is 0 until progress is submitted.",
                quarter,
                year
            );
            aiResult.rawResponse = Map.of("error", "NO_KPI_PROGRESS");
        } else {
            aiResult = geminiService.analyzeQuarterlyPerformance(
                teamName,
                teamLead.getDepartment(),
                kpiData,
                quarter,
                year,
                jobId
            );
        }

        // Find or create quarterly score
        TeamQuarterlyScore score = quarterlyScoreRepository
            .findByTeamLeadIdAndQuarterAndYear(teamLeadId, quarter, year)
            .orElse(new TeamQuarterlyScore(teamLeadId, quarter, year));

        score.setDepartment(teamLead.getDepartment());
        score.setTeamName(teamName);
        score.setKpiAchievementScore(aiResult.kpiScore);

        Double individualAvg = computeIndividualKpiAverage(teamLead.getDepartment(), quarter, year);
        if (individualAvg != null) {
            score.setIndividualAvgScore(BigDecimal.valueOf(individualAvg).setScale(2, RoundingMode.HALF_UP));
        }

        double kpiScore = aiResult.kpiScore != null ? aiResult.kpiScore.doubleValue() : 0.0;
        double overallScore = kpiScore;
        double individualWeight = 0.2;
        double teamWeight = 0.8;
        if (!aiResult.fallback && individualAvg != null) {
            overallScore = (kpiScore * teamWeight) + (individualAvg * individualWeight);
        }
        score.setOverallTeamScore(BigDecimal.valueOf(overallScore).setScale(2, RoundingMode.HALF_UP));
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("teamKpiScore", kpiScore);
        breakdown.put("individualKpiAverage", individualAvg);
        breakdown.put("teamWeight", teamWeight);
        breakdown.put("individualWeight", individualWeight);
        breakdown.put("fallback", aiResult.fallback);
        score.setScoreBreakdown(breakdown);
        score.setAiSummary(aiResult.summary);
        score.setAiRequestId(aiResult.requestId);
        score.setPromptVersion(aiResult.promptVersion);
        score.setModelUsed(aiResult.modelUsed);
        score.calculateGrade();

        return quarterlyScoreRepository.save(score);
    }

    public AiJob enqueueQuarterlyScore(UUID teamLeadId, String quarter, Integer year, UUID requestedBy) {
        return aiJobService.enqueueJob(
            AiJobTypes.KPI_QUARTERLY_SCORE,
            Map.of(
                "teamLeadId", teamLeadId.toString(),
                "quarter", quarter,
                "year", year,
                "requestedBy", requestedBy != null ? requestedBy.toString() : null
            ),
            2
        );
    }

    @Transactional
    public void processQuarterlyScoreJob(UUID jobId, Map<String, Object> payload) {
        UUID teamLeadId = UUID.fromString(payload.get("teamLeadId").toString());
        String quarter = payload.get("quarter").toString();
        Integer year = Integer.valueOf(payload.get("year").toString());
        UUID requestedBy = payload.get("requestedBy") != null ? UUID.fromString(payload.get("requestedBy").toString()) : null;

        calculateQuarterlyScore(teamLeadId, quarter, year, jobId, requestedBy);
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
        Optional<TeamQuarterlyScore> score = quarterlyScoreRepository.findByTeamLeadIdAndQuarterAndYear(teamLeadId, quarter, year);
        score.ifPresent(existing -> profileRepository.findById(teamLeadId).ifPresent(teamLead -> {
            String teamName = resolveTeamName(teamLead);
            if (teamName != null && !teamName.equals(existing.getTeamName())) {
                existing.setTeamName(teamName);
                quarterlyScoreRepository.save(existing);
            }
        }));
        return score;
    }

    // ==================== HELPER METHODS ====================

    private String resolveTeamName(Profile teamLead) {
        Optional<TeamLeadAppointment> appointment = teamLeadRepository
            .findByEmployeeIdAndStatus(teamLead.getId(), TeamLeadAppointment.STATUS_CONFIRMED);
        if (appointment.isEmpty()) {
            appointment = teamLeadRepository
                .findByEmployeeIdAndStatus(teamLead.getId(), TeamLeadAppointment.STATUS_ACTING);
        }
        if (appointment.isEmpty()) {
            appointment = teamLeadRepository.findByEmployeeIdOrderByAppointedAtDesc(teamLead.getId())
                .stream()
                .findFirst();
        }

        if (appointment.isPresent()) {
            String teamName = appointment.get().getTeamName();
            if (teamName != null && !teamName.isBlank()) {
                return teamName.trim();
            }
        }

        String department = teamLead.getDepartment();
        if (department != null && !department.isBlank()) {
            String trimmed = department.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.endsWith("team")) {
                return trimmed;
            }
            return trimmed + " Team";
        }

        return teamLead.getFullName() + "'s Team";
    }

    private BigDecimal calculateWeightedProgressScore(List<GeminiAiService.KpiProgressData> kpiData) {
        double weighted = 0.0;
        double totalWeight = 0.0;

        for (GeminiAiService.KpiProgressData kpi : kpiData) {
            double weight = kpi.weight;
            if (weight <= 0) {
                continue;
            }
            double progress = Math.max(0.0, Math.min(100.0, kpi.progressPercentage));
            weighted += progress * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        double score = weighted / totalWeight;
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private void attachScoreBreakdown(
            GeminiAiService.AiAnalysisResult aiResult,
            BigDecimal kpiProgressScore,
            boolean reportAvailable,
            String scoreSource) {
        if (aiResult == null) {
            return;
        }

        Map<String, Object> raw = aiResult.rawResponse != null ? new HashMap<>(aiResult.rawResponse) : new HashMap<>();
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("kpiProgressScore", kpiProgressScore);
        breakdown.put("aiScore", raw.getOrDefault("aiKpiScore", aiResult.kpiScore));
        breakdown.put("finalScore", aiResult.kpiScore);
        breakdown.put("reportAvailable", reportAvailable);
        breakdown.put("scoreSource", scoreSource);
        breakdown.put("scoreScale", "0-100");

        raw.put("scoreBreakdown", breakdown);
        aiResult.rawResponse = raw;
    }

    private String getQuarterForWeek(int weekNumber) {
        if (weekNumber <= 13) return "Q1";
        if (weekNumber <= 26) return "Q2";
        if (weekNumber <= 39) return "Q3";
        return "Q4";
    }

    private Double computeIndividualKpiAverage(String department, String quarter, Integer year) {
        if (department == null || department.isBlank()) {
            return null;
        }
        List<IndividualKpi> kpis = individualKpiRepository.findByDepartmentAndPeriod(department, quarter, year);
        if (kpis.isEmpty()) {
            return null;
        }
        double weightedSum = 0.0;
        int totalWeight = 0;
        for (IndividualKpi kpi : kpis) {
            if (kpi.getAchievementPercentage() == null || kpi.getWeight() == null) {
                continue;
            }
            weightedSum += kpi.getAchievementPercentage().doubleValue() * kpi.getWeight();
            totalWeight += kpi.getWeight();
        }
        if (totalWeight == 0) {
            return null;
        }
        return weightedSum / totalWeight;
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
        public String progressSource;
        public Map<String, Object> progressConfig;
        public Boolean autoProgressEnabled;
    }

    public static class KpiUpdateRequest {
        public String name;
        public String description;
        public Double targetValue;
        public String targetUnit;
        public Integer weight;
        public Boolean isActive;
        public String progressSource;
        public Map<String, Object> progressConfig;
        public Boolean autoProgressEnabled;
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
