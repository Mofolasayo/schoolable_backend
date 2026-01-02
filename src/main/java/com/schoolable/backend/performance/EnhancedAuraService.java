package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.schoolable.backend.performance.AuraPillarConfig.*;

/**
 * Service for building enhanced Aura dashboard responses with sub-metric breakdown.
 * Works alongside the existing AuraDashboardService but provides more granular data.
 */
@Service
public class EnhancedAuraService {

    @Autowired
    private SubMetricScoreRepository subMetricRepository;

    @Autowired
    private SubMetricCalculationService calculationService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    /**
     * Get enhanced Aura dashboard with all sub-metrics
     */
    public AuraSubMetricDto.EnhancedAuraResponse getEnhancedAuraDashboard(UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId).orElse(null);
        if (profile == null) {
            throw new RuntimeException("Employee not found");
        }

        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        // Ensure metrics are calculated
        calculationService.calculateEmployeeMetrics(employeeId, quarter, year);

        // Build response
        AuraSubMetricDto.EnhancedAuraResponse response = new AuraSubMetricDto.EnhancedAuraResponse();
        response.setEmployeeId(employeeId.toString());
        response.setEmployeeName(profile.getFullName());
        response.setDepartment(profile.getDepartment());
        response.setRole(profile.getRole());
        response.setJobTitle(profile.getJobTitle());

        boolean isTeamLead = isTeamLead(profile);
        response.setIsTeamLead(isTeamLead);
        response.setCurrentQuarter(quarter);
        response.setCurrentYear(year);

        // Get pillar weight based on role
        double pillarWeight = getPillarWeight(isTeamLead);

        // Get all sub-metrics for this employee
        List<SubMetricScore> allScores = subMetricRepository
            .findByEmployeeIdAndQuarterAndYearOrderByPillarAscSubMetricAsc(employeeId, quarter, year);

        Map<String, List<SubMetricScore>> scoresByPillar = allScores.stream()
            .collect(Collectors.groupingBy(SubMetricScore::getPillar));

        // Build each pillar
        response.setTechnicalPillar(buildPillarDetail(PILLAR_TECHNICAL, "Technical Competence", 
            scoresByPillar.get(PILLAR_TECHNICAL), pillarWeight, isLeadershipRole(profile)));
        
        response.setBehavioralPillar(buildPillarDetail(PILLAR_BEHAVIORAL, "Behavioral Competencies",
            scoresByPillar.get(PILLAR_BEHAVIORAL), pillarWeight, false));
        
        response.setCultureFitPillar(buildPillarDetail(PILLAR_CULTURE_FIT, "Culture Fit",
            scoresByPillar.get(PILLAR_CULTURE_FIT), pillarWeight, false));
        
        response.setGrowthPillar(buildPillarDetail(PILLAR_GROWTH, "Growth & Learning",
            scoresByPillar.get(PILLAR_GROWTH), pillarWeight, false));

        // Leadership pillar (Team Leads only)
        if (isTeamLead) {
            response.setLeadershipPillar(buildPillarDetail(PILLAR_LEADERSHIP, "Leadership",
                scoresByPillar.get(PILLAR_LEADERSHIP), pillarWeight, false));
        }

        // Calculate overall Aura score
        double auraScore = 0;
        if (response.getTechnicalPillar() != null) auraScore += response.getTechnicalPillar().getContribution();
        if (response.getBehavioralPillar() != null) auraScore += response.getBehavioralPillar().getContribution();
        if (response.getCultureFitPillar() != null) auraScore += response.getCultureFitPillar().getContribution();
        if (response.getGrowthPillar() != null) auraScore += response.getGrowthPillar().getContribution();
        if (response.getLeadershipPillar() != null) auraScore += response.getLeadershipPillar().getContribution();

        response.setAuraScore(Math.round(auraScore * 100.0) / 100.0);
        response.setQgpa(Math.round((auraScore / 20) * 100.0) / 100.0);
        response.setGrade(calculateGrade(auraScore));

        // Summary stats
        int autoCount = 0, manualCount = 0, peerCount = 0;
        for (SubMetricScore score : allScores) {
            switch (score.getSource()) {
                case SOURCE_AUTO: autoCount++; break;
                case SOURCE_TEAM_LEAD:
                case SOURCE_ADMIN: manualCount++; break;
                case SOURCE_PEER_FEEDBACK:
                case SOURCE_TEAM_FEEDBACK: peerCount++; break;
            }
        }
        response.setTotalSubMetrics(allScores.size());
        response.setAutoCalculatedMetrics(autoCount);
        response.setManualRatings(manualCount);
        response.setPeerFeedbackMetrics(peerCount);

        // Weeks rated
        response.setWeeksRatedThisQuarter(getWeeksRatedThisQuarter(employeeId, quarter, year));

        return response;
    }

    /**
     * Build a pillar detail with all its sub-metrics
     */
    private AuraSubMetricDto.EnhancedPillarDetail buildPillarDetail(
            String pillarKey, String pillarName, List<SubMetricScore> scores, 
            double pillarWeight, boolean isLeadershipTechnical) {

        AuraSubMetricDto.EnhancedPillarDetail pillar = new AuraSubMetricDto.EnhancedPillarDetail();
        pillar.setName(pillarName);
        pillar.setWeight(pillarWeight);

        List<AuraSubMetricDto.SubMetricDetail> subMetrics = new ArrayList<>();

        if (scores == null || scores.isEmpty()) {
            // No scores yet - return placeholder
            pillar.setScore(50.0);
            pillar.setContribution(pillarWeight / 2);
            pillar.setDataSource("pending");
            pillar.setSubMetrics(Collections.emptyList());
            return pillar;
        }

        // Build sub-metric details
        Set<String> sources = new HashSet<>();
        double totalScore = 0;

        for (SubMetricScore score : scores) {
            AuraSubMetricDto.SubMetricDetail detail = new AuraSubMetricDto.SubMetricDetail();
            detail.setKey(score.getSubMetric());
            detail.setDisplayName(getSubMetricDisplayName(score.getSubMetric()));
            detail.setScore(score.getScore());
            detail.setSource(score.getSource());
            detail.setWeightInPillar(20.0); // 5 sub-metrics × 20% = 100%
            detail.setContribution(score.getScore() * 0.20); // Within pillar

            subMetrics.add(detail);
            sources.add(score.getSource());
            totalScore += score.getScore();
        }

        // Calculate pillar averages
        double avgScore = subMetrics.isEmpty() ? 50.0 : totalScore / subMetrics.size();
        pillar.setScore(Math.round(avgScore * 100.0) / 100.0);
        pillar.setContribution(Math.round(avgScore * pillarWeight / 100.0 * 100.0) / 100.0);

        // Determine data source label
        if (sources.size() == 1 && sources.contains(SOURCE_AUTO)) {
            pillar.setDataSource("auto");
        } else if (sources.contains(SOURCE_AUTO)) {
            pillar.setDataSource("mixed");
        } else {
            pillar.setDataSource("manual");
        }

        pillar.setSubMetrics(subMetrics);

        return pillar;
    }

    /**
     * Admin endpoint to rate leadership technical metrics
     */
    public void rateLeadershipMetric(UUID employeeId, String subMetric, int score, UUID adminId) {
        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        // Validate score (1-5)
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }

        double score100 = score * 20.0; // Convert to 0-100

        Optional<SubMetricScore> existing = subMetricRepository
            .findByEmployeeIdAndPillarAndSubMetricAndQuarterAndYear(
                employeeId, PILLAR_TECHNICAL, subMetric, quarter, year);

        SubMetricScore sms;
        if (existing.isPresent()) {
            sms = existing.get();
            sms.setScore(score100);
            sms.setSource(SOURCE_ADMIN);
        } else {
            sms = new SubMetricScore(employeeId, PILLAR_TECHNICAL, subMetric, score100, SOURCE_ADMIN, quarter, year);
        }

        subMetricRepository.save(sms);
    }

    /**
     * Rate leadership pillar metrics (admin only)
     */
    public void rateLeadershipPillarMetric(UUID employeeId, String subMetric, int score, UUID adminId) {
        String quarter = getCurrentQuarter();
        int year = LocalDate.now().getYear();

        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }

        double score100 = score * 20.0;

        Optional<SubMetricScore> existing = subMetricRepository
            .findByEmployeeIdAndPillarAndSubMetricAndQuarterAndYear(
                employeeId, PILLAR_LEADERSHIP, subMetric, quarter, year);

        SubMetricScore sms;
        if (existing.isPresent()) {
            sms = existing.get();
            sms.setScore(score100);
            sms.setSource(SOURCE_ADMIN);
        } else {
            sms = new SubMetricScore(employeeId, PILLAR_LEADERSHIP, subMetric, score100, SOURCE_ADMIN, quarter, year);
        }

        subMetricRepository.save(sms);
    }

    // ==================== HELPER METHODS ====================

    private boolean isLeadershipRole(Profile profile) {
        if (profile == null) return false;
        String role = profile.getRole();
        String jobTitle = profile.getJobTitle();

        return "team_lead".equalsIgnoreCase(role) ||
               "manager".equalsIgnoreCase(role) ||
               (jobTitle != null && (
                   jobTitle.toLowerCase().contains("lead") ||
                   jobTitle.toLowerCase().contains("manager") ||
                   jobTitle.toLowerCase().contains("director") ||
                   jobTitle.toLowerCase().contains("head")
               ));
    }

    private boolean isTeamLead(Profile profile) {
        return profile != null && "team_lead".equalsIgnoreCase(profile.getRole());
    }

    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    private String calculateGrade(double score) {
        if (score >= 86) return "A";
        if (score >= 76) return "B";
        if (score >= 66) return "C";
        if (score >= 50) return "D";
        return "F";
    }

    private int getWeeksRatedThisQuarter(UUID employeeId, String quarter, int year) {
        LocalDate quarterStart = getQuarterStartDate(quarter, year);
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdAndWeekStartDateAfter(employeeId, quarterStart);
        return reports.size();
    }

    private LocalDate getQuarterStartDate(String quarter, int year) {
        switch (quarter) {
            case "Q1": return LocalDate.of(year, 1, 1);
            case "Q2": return LocalDate.of(year, 4, 1);
            case "Q3": return LocalDate.of(year, 7, 1);
            case "Q4": return LocalDate.of(year, 10, 1);
            default: return LocalDate.of(year, 1, 1);
        }
    }
}
