package com.schoolable.backend.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.ai.AiJob;
import com.schoolable.backend.ai.AiJobService;
import com.schoolable.backend.ai.AiJobTypes;
import com.schoolable.backend.kpi.GeminiAiService;
import com.schoolable.backend.kpi.IndividualKpi;
import com.schoolable.backend.kpi.IndividualKpiRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DailyReportAiService {

    private final DailyReportRepository dailyReportRepository;
    private final ProfileRepository profileRepository;
    private final IndividualKpiRepository individualKpiRepository;
    private final GeminiAiService geminiAiService;
    private final ObjectMapper objectMapper;
    private final AiJobService aiJobService;

    public DailyReportAiService(
            DailyReportRepository dailyReportRepository,
            ProfileRepository profileRepository,
            IndividualKpiRepository individualKpiRepository,
            GeminiAiService geminiAiService,
            ObjectMapper objectMapper,
            AiJobService aiJobService) {
        this.dailyReportRepository = dailyReportRepository;
        this.profileRepository = profileRepository;
        this.individualKpiRepository = individualKpiRepository;
        this.geminiAiService = geminiAiService;
        this.objectMapper = objectMapper;
        this.aiJobService = aiJobService;
    }

    public AiJob enqueueAiGrading(Long reportId, UUID employeeId) {
        AiJob job = aiJobService.enqueueJob(
            AiJobTypes.DAILY_REPORT_GRADE,
            Map.of("reportId", reportId, "employeeId", employeeId.toString()),
            5
        );

        dailyReportRepository.findById(reportId).ifPresent(report -> {
            report.setAiJobId(job.getId());
            report.setAiStatus("PENDING");
            dailyReportRepository.save(report);
        });

        return job;
    }

    @Transactional
    public void processDailyReportJob(UUID jobId, Map<String, Object> payload) {
        Long reportId = payload.get("reportId") != null ? Long.valueOf(payload.get("reportId").toString()) : null;
        if (reportId == null) {
            return;
        }

        DailyReport report = dailyReportRepository.findById(reportId).orElse(null);
        if (report == null) {
            return;
        }

        UUID employeeId = report.getEmployeeId();
        Profile employee = profileRepository.findById(employeeId).orElse(null);
        String department = employee != null ? employee.getDepartment() : null;
        String employeeName = employee != null ? employee.getFullName() : null;

        String quarter = getCurrentQuarter();
        int year = OffsetDateTime.now().getYear();
        List<IndividualKpi> kpis = individualKpiRepository.findActiveByEmployeeAndPeriod(employeeId, quarter, year);

        List<String> kpiNames = new ArrayList<>();
        for (IndividualKpi kpi : kpis) {
            kpiNames.add(kpi.getName() + " (Target: " + kpi.getTargetValue() + " " +
                (kpi.getTargetUnit() != null ? kpi.getTargetUnit() : "") +
                ", Weight: " + kpi.getWeight() + "%)");
        }

        GeminiAiService.DailyReportGradingResult result = geminiAiService.gradeDailyReport(
            employeeName,
            department,
            report.getTasksCompleted(),
            report.getTasksInProgress(),
            report.getBlockers(),
            report.getPlannedForTomorrow(),
            report.getAdditionalNotes(),
            kpiNames,
            jobId
        );

        if (result.overallScore != null) {
            report.setAiScore(result.overallScore);
        }
        if (result.feedback != null) {
            report.setAiFeedback(result.feedback);
        }
        if (result.kpiAlignmentScore == null) {
            result.kpiAlignmentScore = result.overallScore != null
                ? result.overallScore
                : java.math.BigDecimal.ZERO;
        }
        report.setKpiAlignmentScore(result.kpiAlignmentScore);
        if (result.suggestionsForTomorrow != null && !result.suggestionsForTomorrow.isEmpty()) {
            try {
                report.setAiSuggestions(objectMapper.writeValueAsString(result.suggestionsForTomorrow));
            } catch (Exception e) {
                report.setAiSuggestions(null);
            }
        }

        if (result.strengths != null && !result.strengths.isEmpty()) {
            try {
                report.setAiStrengths(objectMapper.writeValueAsString(result.strengths));
            } catch (Exception e) {
                report.setAiStrengths(null);
            }
        } else {
            report.setAiStrengths(null);
        }

        if (result.improvements != null && !result.improvements.isEmpty()) {
            try {
                report.setAiImprovements(objectMapper.writeValueAsString(result.improvements));
            } catch (Exception e) {
                report.setAiImprovements(null);
            }
        } else {
            report.setAiImprovements(null);
        }

        if (result.auraBoostTips != null && !result.auraBoostTips.isEmpty()) {
            try {
                report.setAiAuraBoostTips(objectMapper.writeValueAsString(result.auraBoostTips));
            } catch (Exception e) {
                report.setAiAuraBoostTips(null);
            }
        } else {
            report.setAiAuraBoostTips(null);
        }

        report.setAiGradedAt(OffsetDateTime.now());
        report.setAiJobId(jobId);
        report.setAiRequestId(result.requestId);
        report.setAiPromptVersion(result.promptVersion);
        report.setAiModelUsed(result.modelUsed);
        report.setAiStatus(result.requestId != null ? "COMPLETED" : "COMPLETED");
        dailyReportRepository.save(report);
    }

    private String getCurrentQuarter() {
        int month = OffsetDateTime.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }
}
