package com.schoolable.backend.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
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
public class AuraScoreJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AuraScoreJobProcessor.class);

    private final AuraScoreJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final AutoAuraCalculationService autoAuraCalculationService;
    private final ProfileRepository profileRepository;

    public AuraScoreJobProcessor(
            AuraScoreJobRepository jobRepository,
            ObjectMapper objectMapper,
            AutoAuraCalculationService autoAuraCalculationService,
            ProfileRepository profileRepository) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.autoAuraCalculationService = autoAuraCalculationService;
        this.profileRepository = profileRepository;
    }

    @Scheduled(fixedDelayString = "${aura.jobs.poll-delay-ms:5000}")
    @Transactional
    public void processJobs() {
        List<AuraScoreJob> jobs = jobRepository.findDueJobs(AuraScoreJob.Status.PENDING, OffsetDateTime.now());
        for (AuraScoreJob job : jobs) {
            processJob(job);
        }
    }

    private void processJob(AuraScoreJob job) {
        job.setStatus(AuraScoreJob.Status.RUNNING);
        jobRepository.save(job);

        try {
            Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
            switch (job.getJobType()) {
                case AuraScoreJobTypes.AUTO_RECALCULATE_ALL -> recalculateAll();
                case AuraScoreJobTypes.AUTO_RECALCULATE_EMPLOYEE -> recalculateEmployee(payload);
                default -> log.warn("Unknown Aura job type: {}", job.getJobType());
            }

            job.setStatus(AuraScoreJob.Status.COMPLETED);
            jobRepository.save(job);
        } catch (Exception e) {
            handleFailure(job, e);
        }
    }

    private void recalculateAll() {
        List<Profile> employees = profileRepository.findAll();
        for (Profile employee : employees) {
            autoAuraCalculationService.calculateAndSaveEmployeeScore(employee);
        }
    }

    private void recalculateEmployee(Map<String, Object> payload) {
        Object employeeId = payload.get("employeeId");
        if (employeeId == null) {
            return;
        }
        UUID id = UUID.fromString(employeeId.toString());
        Profile profile = profileRepository.findById(id).orElse(null);
        if (profile != null) {
            autoAuraCalculationService.calculateAndSaveEmployeeScore(profile);
        }
    }

    private void handleFailure(AuraScoreJob job, Exception e) {
        int attempts = job.getAttempts() != null ? job.getAttempts() + 1 : 1;
        job.setAttempts(attempts);
        job.setLastError(e.getMessage());

        if (job.getMaxAttempts() != null && attempts >= job.getMaxAttempts()) {
            job.setStatus(AuraScoreJob.Status.DEAD);
            jobRepository.save(job);
            log.error("Aura job {} failed after {} attempts", job.getId(), attempts, e);
            return;
        }

        long delayMinutes = backoffMinutes(attempts);
        job.setNextRunAt(OffsetDateTime.now().plusMinutes(delayMinutes));
        job.setStatus(AuraScoreJob.Status.PENDING);
        jobRepository.save(job);
        log.warn("Aura job {} failed attempt {}. Retrying in {} minutes", job.getId(), attempts, delayMinutes, e);
    }

    private long backoffMinutes(int attempt) {
        long base = 2L;
        long delay = (long) Math.pow(base, Math.min(attempt, 5));
        return Math.min(delay, 60L);
    }
}
