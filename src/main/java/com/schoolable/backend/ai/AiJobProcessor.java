package com.schoolable.backend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.kpi.KpiAnalysisService;
import com.schoolable.backend.performance.DailyReportAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiJobProcessor.class);

    private final AiJobRepository aiJobRepository;
    private final ObjectMapper objectMapper;
    private final DailyReportAiService dailyReportAiService;
    private final KpiAnalysisService kpiAnalysisService;

    public AiJobProcessor(
            AiJobRepository aiJobRepository,
            ObjectMapper objectMapper,
            DailyReportAiService dailyReportAiService,
            KpiAnalysisService kpiAnalysisService) {
        this.aiJobRepository = aiJobRepository;
        this.objectMapper = objectMapper;
        this.dailyReportAiService = dailyReportAiService;
        this.kpiAnalysisService = kpiAnalysisService;
    }

    @Scheduled(fixedDelayString = "${ai.jobs.poll-delay-ms:5000}")
    @Transactional
    public void processJobs() {
        List<AiJob> jobs = aiJobRepository.findDueJobs(AiJob.Status.PENDING, OffsetDateTime.now());
        for (AiJob job : jobs) {
            processJob(job);
        }
    }

    private void processJob(AiJob job) {
        job.setStatus(AiJob.Status.RUNNING);
        aiJobRepository.save(job);

        try {
            Map<String, Object> payload = job.getPayload() == null
                ? Map.of()
                : objectMapper.convertValue(job.getPayload(), new TypeReference<>() {});
            switch (job.getJobType()) {
                case AiJobTypes.DAILY_REPORT_GRADE -> dailyReportAiService.processDailyReportJob(job.getId(), payload);
                case AiJobTypes.KPI_WEEKLY_INSIGHT -> kpiAnalysisService.processWeeklyInsightJob(job.getId(), payload);
                case AiJobTypes.KPI_QUARTERLY_SCORE -> kpiAnalysisService.processQuarterlyScoreJob(job.getId(), payload);
                default -> log.warn("Unknown AI job type: {}", job.getJobType());
            }

            job.setStatus(AiJob.Status.COMPLETED);
            aiJobRepository.save(job);
        } catch (Exception e) {
            handleFailure(job, e);
        }
    }

    private void handleFailure(AiJob job, Exception e) {
        int attempts = job.getAttempts() != null ? job.getAttempts() + 1 : 1;
        job.setAttempts(attempts);
        job.setLastError(e.getMessage());

        if (job.getMaxAttempts() != null && attempts >= job.getMaxAttempts()) {
            job.setStatus(AiJob.Status.DEAD);
            aiJobRepository.save(job);
            log.error("AI job {} failed after {} attempts", job.getId(), attempts, e);
            return;
        }

        long delayMinutes = backoffMinutes(attempts);
        job.setNextRunAt(OffsetDateTime.now().plusMinutes(delayMinutes));
        job.setStatus(AiJob.Status.PENDING);
        aiJobRepository.save(job);
        log.warn("AI job {} failed attempt {}. Retrying in {} minutes", job.getId(), attempts, delayMinutes, e);
    }

    private long backoffMinutes(int attempt) {
        long base = 2L;
        long delay = (long) Math.pow(base, Math.min(attempt, 5));
        return Math.min(delay, 60L);
    }
}
