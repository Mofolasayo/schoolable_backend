package com.schoolable.backend.performance;

import com.schoolable.backend.kpi.KpiAnalysisService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

    private final WeeklyReportRepository weeklyReportRepository;
    private final ProfileRepository profileRepository;
    private final KpiAnalysisService kpiAnalysisService;

    public WeeklyReportService(WeeklyReportRepository weeklyReportRepository, 
                               ProfileRepository profileRepository,
                               KpiAnalysisService kpiAnalysisService) {
        this.weeklyReportRepository = weeklyReportRepository;
        this.profileRepository = profileRepository;
        this.kpiAnalysisService = kpiAnalysisService;
    }

    /**
     * Submit a single weekly report for a team member
     */
    @Transactional
    public WeeklyReportDto.ReportResponse submitReport(UUID teamLeadId, WeeklyReportDto.SingleReportRequest request) {
        // Verify team lead
        Profile teamLead = profileRepository.findById(teamLeadId)
                .orElseThrow(() -> new RuntimeException("Team lead not found"));

        if (!Boolean.TRUE.equals(teamLead.getIsTeamLead())) {
            throw new RuntimeException("Only team leads can submit weekly reports");
        }

        UUID employeeId = UUID.fromString(request.getEmployeeId());

        // Check for existing report
        Optional<WeeklyPerformanceReport> existing = weeklyReportRepository
                .findByEmployeeIdAndWeekNumberAndYear(employeeId, request.getWeekNumber(), request.getYear());

        WeeklyPerformanceReport report;
        if (existing.isPresent()) {
            report = existing.get();
        } else {
            report = new WeeklyPerformanceReport();
            report.setEmployeeId(employeeId);
            report.setWeekNumber(request.getWeekNumber());
            report.setYear(request.getYear());
        }

        // Calculate week dates
        LocalDate weekStart = getWeekStartDate(request.getYear(), request.getWeekNumber());
        LocalDate weekEnd = weekStart.plusDays(6);

        report.setWeekStartDate(weekStart);
        report.setWeekEndDate(weekEnd);
        report.setReviewerId(teamLeadId);

        // Set scores (1-5)
        report.setTechnicalScore(request.getTechnicalScore());
        report.setBehavioralScore(request.getBehavioralScore());
        report.setCultureFitScore(request.getCultureFitScore());
        report.setGrowthLearningScore(request.getGrowthLearningScore());

        // Set notes
        report.setTechnicalNotes(request.getTechnicalNotes());
        report.setBehavioralNotes(request.getBehavioralNotes());
        report.setCultureFitNotes(request.getCultureFitNotes());
        report.setGrowthLearningNotes(request.getGrowthLearningNotes());
        report.setWeeklyHighlights(request.getWeeklyHighlights());
        report.setAreasForFocus(request.getAreasForFocus());

        report.setStatus("submitted");

        WeeklyPerformanceReport saved = weeklyReportRepository.save(report);

        Profile employee = profileRepository.findById(employeeId).orElse(null);
        return mapToResponse(saved, employee, teamLead);
    }

    /**
     * Submit batch reports for all team members in a week
     */
    @Transactional
    public List<WeeklyReportDto.ReportResponse> submitBatchReports(UUID teamLeadId, WeeklyReportDto.BatchReportRequest request) {
        Profile teamLead = profileRepository.findById(teamLeadId)
                .orElseThrow(() -> new RuntimeException("Team lead not found"));

        if (!Boolean.TRUE.equals(teamLead.getIsTeamLead())) {
            throw new RuntimeException("Only team leads can submit weekly reports");
        }

        List<WeeklyReportDto.ReportResponse> responses = new ArrayList<>();

        for (WeeklyReportDto.TeamMemberWeeklyReport memberReport : request.getReports()) {
            WeeklyReportDto.SingleReportRequest singleRequest = new WeeklyReportDto.SingleReportRequest();
            singleRequest.setEmployeeId(memberReport.getEmployeeId());
            singleRequest.setWeekNumber(request.getWeekNumber());
            singleRequest.setYear(request.getYear());
            singleRequest.setTechnicalScore(memberReport.getTechnicalScore());
            singleRequest.setBehavioralScore(memberReport.getBehavioralScore());
            singleRequest.setCultureFitScore(memberReport.getCultureFitScore());
            singleRequest.setGrowthLearningScore(memberReport.getGrowthLearningScore());
            singleRequest.setTechnicalNotes(memberReport.getTechnicalNotes());
            singleRequest.setBehavioralNotes(memberReport.getBehavioralNotes());
            singleRequest.setCultureFitNotes(memberReport.getCultureFitNotes());
            singleRequest.setGrowthLearningNotes(memberReport.getGrowthLearningNotes());
            singleRequest.setWeeklyHighlights(memberReport.getWeeklyHighlights());
            singleRequest.setAreasForFocus(memberReport.getAreasForFocus());

            try {
                responses.add(submitReport(teamLeadId, singleRequest));
            } catch (Exception e) {
                log.warn("Failed to submit report for employee {}: {}", memberReport.getEmployeeId(), e.getMessage());
            }
        }

        return responses;
    }

    /**
     * Submit SIMPLIFIED reports (3 ratings only) for all team members in a week
     * Used by the Team Lead dashboard
     */
    @Transactional
    public List<WeeklyReportDto.ReportResponse> submitSimplifiedReports(UUID teamLeadId, WeeklyReportDto.SimplifiedBatchRequest request) {
        Profile teamLead = profileRepository.findById(teamLeadId)
                .orElseThrow(() -> new RuntimeException("Team lead not found"));

        if (!Boolean.TRUE.equals(teamLead.getIsTeamLead())) {
            throw new RuntimeException("Only team leads can submit weekly reports");
        }

        // Calculate week dates
        LocalDate weekStart = getWeekStartDate(request.getYear(), request.getWeekNumber());
        LocalDate weekEnd = weekStart.plusDays(6);

        List<WeeklyReportDto.ReportResponse> responses = new ArrayList<>();

        for (WeeklyReportDto.SimplifiedTeamMemberRating rating : request.getRatings()) {
            try {
                UUID employeeId = UUID.fromString(rating.getEmployeeId());

                // Check for existing report
                Optional<WeeklyPerformanceReport> existing = weeklyReportRepository
                        .findByEmployeeIdAndWeekNumberAndYear(employeeId, request.getWeekNumber(), request.getYear());

                WeeklyPerformanceReport report;
                if (existing.isPresent()) {
                    report = existing.get();
                } else {
                    report = new WeeklyPerformanceReport();
                    report.setEmployeeId(employeeId);
                    report.setWeekNumber(request.getWeekNumber());
                    report.setYear(request.getYear());
                }

                report.setWeekStartDate(weekStart);
                report.setWeekEndDate(weekEnd);
                report.setReviewerId(teamLeadId);

                // Set the 3 simplified ratings
                report.setTeamworkCollaborationScore(rating.getTeamworkCollaborationScore());
                report.setInitiativeScore(rating.getInitiativeScore());
                report.setAttitudeTowardsWorkScore(rating.getAttitudeTowardsWorkScore());
                
                // CRITICAL: Map simplified ratings to core pillar scores to satisfy NOT NULL constraints
                // Initiative → Technical Competence
                report.setTechnicalScore(rating.getInitiativeScore());
                
                // Attitude → Behavioral Compliance  
                report.setBehavioralScore(rating.getAttitudeTowardsWorkScore());
                
                // Teamwork → Culture Fit
                report.setCultureFitScore(rating.getTeamworkCollaborationScore());
                
                // Growth/Learning: Average of the three (rounded)
                int growthScore = Math.round((rating.getInitiativeScore() + rating.getAttitudeTowardsWorkScore() + rating.getTeamworkCollaborationScore()) / 3.0f);
                report.setGrowthLearningScore(growthScore);
                
                // Set team report URL if provided
                if (request.getTeamReportUrl() != null) {
                    report.setTeamReportUrl(request.getTeamReportUrl());
                }

                // Store notes in behavioral notes for now
                if (rating.getNotes() != null) {
                    report.setBehavioralNotes(rating.getNotes());
                }
                if (rating.getWeeklyHighlights() != null) {
                    report.setWeeklyHighlights(rating.getWeeklyHighlights());
                }
                if (rating.getAreasForFocus() != null) {
                    report.setAreasForFocus(rating.getAreasForFocus());
                }

                report.setStatus("submitted");

                WeeklyPerformanceReport saved = weeklyReportRepository.save(report);
                Profile employee = profileRepository.findById(employeeId).orElse(null);
                responses.add(mapToResponse(saved, employee, teamLead));
            } catch (Exception e) {
                log.warn("Failed to submit simplified report for employee {}: {}", rating.getEmployeeId(), e.getMessage());
            }
        }

        // Auto-generate quarterly score to populate Teams Overview dashboard
        try {
            String quarter = kpiAnalysisService.getCurrentQuarter();
            kpiAnalysisService.calculateQuarterlyScore(teamLeadId, quarter, request.getYear());
        } catch (Exception e) {
            log.warn("Failed to auto-generate quarterly score: {}", e.getMessage());
        }

        return responses;
    }

    /**
     * TEMPORARY: Delete a corrupted weekly report
     */
    @Transactional
    public void deleteReport(Long reportId) {
        weeklyReportRepository.deleteById(reportId);
    }

    /**
     * Get all reports for a specific week (admin view)
     */
    public List<WeeklyReportDto.ReportResponse> getWeeklyReports(Integer weekNumber, Integer year) {
        List<WeeklyPerformanceReport> reports = weeklyReportRepository.findByWeekNumberAndYearOrderByCreatedAtDesc(weekNumber, year);
        return reports.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    /**
     * Get reports submitted by a team lead for a specific week
     */
    public List<WeeklyReportDto.ReportResponse> getTeamLeadWeeklyReports(UUID teamLeadId, Integer weekNumber, Integer year) {
        List<WeeklyPerformanceReport> reports = weeklyReportRepository.findByReviewerIdAndWeekNumberAndYear(teamLeadId, weekNumber, year);
        return reports.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    /**
     * Get employee's weekly trend for current year
     */
    public List<WeeklyReportDto.ReportResponse> getEmployeeWeeklyTrend(UUID employeeId, Integer year) {
        List<WeeklyPerformanceReport> reports = weeklyReportRepository.findByEmployeeIdAndYearOrderByWeekNumberDesc(employeeId, year);
        return reports.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    /**
     * Get employee's full history
     */
    public List<WeeklyReportDto.ReportResponse> getEmployeeHistory(UUID employeeId) {
        List<WeeklyPerformanceReport> reports = weeklyReportRepository.findByEmployeeIdOrderByYearDescWeekNumberDesc(employeeId);
        return reports.stream()
                .map(this::mapToResponseWithLookup)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTeamReportDocuments(LocalDate startDate, LocalDate endDate) {
        List<WeeklyPerformanceReport> reports = weeklyReportRepository.findTeamReportsInRange(startDate, endDate);
        Map<String, WeeklyPerformanceReport> uniqueReports = new LinkedHashMap<>();

        for (WeeklyPerformanceReport report : reports) {
            if (report.getTeamReportUrl() == null || report.getTeamReportUrl().isBlank()) {
                continue;
            }
            if (report.getReviewerId() == null) {
                continue;
            }
            String key = report.getReviewerId() + "|" + report.getYear() + "|" + report.getWeekNumber() + "|" + report.getTeamReportUrl();
            uniqueReports.putIfAbsent(key, report);
        }

        Set<UUID> reviewerIds = uniqueReports.values().stream()
            .map(WeeklyPerformanceReport::getReviewerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, Profile> reviewerMap = profileRepository.findAllById(reviewerIds).stream()
            .collect(Collectors.toMap(Profile::getId, p -> p));

        List<Map<String, Object>> results = new ArrayList<>();
        for (WeeklyPerformanceReport report : uniqueReports.values()) {
            Profile reviewer = reviewerMap.get(report.getReviewerId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", report.getId());
            item.put("teamLeadId", report.getReviewerId());
            item.put("teamLeadName", reviewer != null ? reviewer.getFullName() : null);
            item.put("department", reviewer != null ? reviewer.getDepartment() : null);
            item.put("weekNumber", report.getWeekNumber());
            item.put("year", report.getYear());
            item.put("weekStartDate", report.getWeekStartDate() != null ? report.getWeekStartDate().toString() : null);
            item.put("weekEndDate", report.getWeekEndDate() != null ? report.getWeekEndDate().toString() : null);
            item.put("teamReportUrl", report.getTeamReportUrl());
            results.add(item);
        }

        return results;
    }

    // Helper methods
    private LocalDate getWeekStartDate(int year, int weekNumber) {
        WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 4);
        return LocalDate.of(year, 1, 1)
                .with(weekFields.weekOfYear(), weekNumber)
                .with(DayOfWeek.MONDAY);
    }

    private WeeklyReportDto.ReportResponse mapToResponse(WeeklyPerformanceReport report, Profile employee, Profile reviewer) {
        WeeklyReportDto.ReportResponse response = new WeeklyReportDto.ReportResponse();
        response.setId(report.getId());
        response.setEmployeeId(report.getEmployeeId().toString());
        response.setEmployeeName(employee != null ? employee.getFullName() : null);
        response.setDepartment(employee != null ? employee.getDepartment() : null);
        response.setWeekNumber(report.getWeekNumber());
        response.setYear(report.getYear());
        response.setWeekStartDate(report.getWeekStartDate() != null ? report.getWeekStartDate().toString() : null);
        response.setWeekEndDate(report.getWeekEndDate() != null ? report.getWeekEndDate().toString() : null);

        // Scores (1-5)
        response.setTechnicalScore(report.getTechnicalScore());
        response.setBehavioralScore(report.getBehavioralScore());
        response.setCultureFitScore(report.getCultureFitScore());
        response.setGrowthLearningScore(report.getGrowthLearningScore());

        // Scores as percentages (1=20%, 2=40%, 3=60%, 4=80%, 5=100%)
        response.setTechnicalPct(report.getTechnicalScore() * 20);
        response.setBehavioralPct(report.getBehavioralScore() * 20);
        response.setCultureFitPct(report.getCultureFitScore() * 20);
        response.setGrowthLearningPct(report.getGrowthLearningScore() * 20);

        // Weekly Aura (auto-calculated in DB, or calculate here)
        if (report.getWeeklyAura() != null) {
            response.setWeeklyAura(report.getWeeklyAura().doubleValue());
        } else {
            double aura = ((report.getTechnicalScore() + report.getBehavioralScore() + 
                           report.getCultureFitScore() + report.getGrowthLearningScore()) / 4.0) * 20;
            response.setWeeklyAura(aura);
        }

        response.setGrade(calculateGrade(response.getWeeklyAura()));

        response.setTechnicalNotes(report.getTechnicalNotes());
        response.setBehavioralNotes(report.getBehavioralNotes());
        response.setCultureFitNotes(report.getCultureFitNotes());
        response.setGrowthLearningNotes(report.getGrowthLearningNotes());
        response.setWeeklyHighlights(report.getWeeklyHighlights());
        response.setAreasForFocus(report.getAreasForFocus());

        response.setReviewerName(reviewer != null ? reviewer.getFullName() : null);
        response.setCreatedAt(report.getCreatedAt() != null ? report.getCreatedAt().toString() : null);

        return response;
    }

    private WeeklyReportDto.ReportResponse mapToResponseWithLookup(WeeklyPerformanceReport report) {
        Profile employee = profileRepository.findById(report.getEmployeeId()).orElse(null);
        Profile reviewer = profileRepository.findById(report.getReviewerId()).orElse(null);
        return mapToResponse(report, employee, reviewer);
    }

    private String calculateGrade(Double aura) {
        if (aura == null) return null;
        double gpa = aura / 20;
        if (gpa >= 4.30) return "A";
        if (gpa >= 3.80) return "B";
        if (gpa >= 3.30) return "C";
        if (gpa >= 2.50) return "D";
        return "F";
    }
}
