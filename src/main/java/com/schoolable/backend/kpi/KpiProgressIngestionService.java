package com.schoolable.backend.kpi;

import com.schoolable.backend.performance.DailyReportRepository;
import com.schoolable.backend.task.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class KpiProgressIngestionService {

    private static final List<String> COMPLETED_STATUSES = List.of("DONE", "COMPLETED", "Done", "Completed");

    private final TeamKpiRepository teamKpiRepository;
    private final IndividualKpiRepository individualKpiRepository;
    private final WeeklyKpiProgressRepository progressRepository;
    private final TaskRepository taskRepository;
    private final DailyReportRepository dailyReportRepository;

    public KpiProgressIngestionService(
            TeamKpiRepository teamKpiRepository,
            IndividualKpiRepository individualKpiRepository,
            WeeklyKpiProgressRepository progressRepository,
            TaskRepository taskRepository,
            DailyReportRepository dailyReportRepository) {
        this.teamKpiRepository = teamKpiRepository;
        this.individualKpiRepository = individualKpiRepository;
        this.progressRepository = progressRepository;
        this.taskRepository = taskRepository;
        this.dailyReportRepository = dailyReportRepository;
    }

    @Scheduled(cron = "0 15 1 * * *")
    public void ingestCurrentWeek() {
        LocalDate now = LocalDate.now();
        int weekNumber = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        int year = now.getYear();
        ingestWeek(weekNumber, year);
    }

    @Transactional
    public void ingestWeek(int weekNumber, int year) {
        String quarter = getQuarterForWeek(weekNumber);
        LocalDate weekStart = getWeekStartDate(weekNumber, year);
        LocalDate weekEnd = weekStart.plusDays(6);

        OffsetDateTime start = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = weekEnd.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

        List<TeamKpi> teamKpis = teamKpiRepository.findAllActiveByQuarterAndYear(quarter, year)
            .stream()
            .filter(kpi -> isAutoProgressEligible(kpi.getProgressSource()))
            .toList();
        for (TeamKpi kpi : teamKpis) {
            Double achieved = computeTeamAchieved(kpi, weekStart, weekEnd, start, end);
            if (achieved == null) continue;

            upsertWeeklyProgress(kpi.getId(), kpi.getTargetValue(), achieved, weekNumber, year, kpi.getTeamLeadId());
            kpi.setLastProgressSyncAt(OffsetDateTime.now());
            teamKpiRepository.save(kpi);
        }

        List<IndividualKpi> individualKpis = individualKpiRepository.findByQuarterAndYearAndIsActiveTrue(quarter, year)
            .stream()
            .filter(kpi -> isAutoProgressEligible(kpi.getProgressSource()))
            .toList();
        for (IndividualKpi kpi : individualKpis) {
            Double achieved = computeIndividualAchieved(kpi, weekStart, weekEnd, start, end);
            if (achieved == null) continue;

            upsertWeeklyProgress(kpi.getId(), kpi.getTargetValue(), achieved, weekNumber, year, kpi.getSetById());
            updateIndividualCurrentValue(kpi, year);
            kpi.setLastProgressSyncAt(OffsetDateTime.now());
            individualKpiRepository.save(kpi);
        }
    }

    private Double computeTeamAchieved(TeamKpi kpi, LocalDate startDate, LocalDate endDate, OffsetDateTime start, OffsetDateTime end) {
        if (kpi.getProgressSource() == null) return null;

        return switch (kpi.getProgressSource()) {
            case "TASKS_COMPLETED" -> (double) taskRepository.countByDepartmentStatusAndUpdatedAtBetween(
                kpi.getDepartment(), COMPLETED_STATUSES, start, end
            );
            case "DAILY_REPORT_COUNT" -> {
                Long count = dailyReportRepository.countByDepartmentAndDateRange(kpi.getDepartment(), startDate, endDate);
                yield count != null ? count.doubleValue() : 0.0;
            }
            case "DAILY_REPORT_SCORE_AVG" -> {
                Double avg = dailyReportRepository.getAverageAiScoreForDepartment(kpi.getDepartment(), startDate, endDate);
                yield avg != null ? avg : 0.0;
            }
            case "DAILY_REPORT_KPI_ALIGNMENT", "KPI_ALIGNMENT" -> {
                Double avg = dailyReportRepository.getAverageKpiAlignmentScoreForDepartment(kpi.getDepartment(), startDate, endDate);
                yield avg != null ? avg : 0.0;
            }
            default -> null;
        };
    }

    private Double computeIndividualAchieved(IndividualKpi kpi, LocalDate startDate, LocalDate endDate, OffsetDateTime start, OffsetDateTime end) {
        if (kpi.getProgressSource() == null) return null;

        return switch (kpi.getProgressSource()) {
            case "TASKS_COMPLETED" -> (double) taskRepository.countByAssigneeStatusAndUpdatedAtBetween(
                kpi.getEmployeeId(), COMPLETED_STATUSES, start, end
            );
            case "DAILY_REPORT_COUNT" -> {
                Long count = dailyReportRepository.countByEmployeeAndWeek(kpi.getEmployeeId(), startDate, endDate);
                yield count != null ? count.doubleValue() : 0.0;
            }
            case "DAILY_REPORT_SCORE_AVG" -> {
                Double avg = dailyReportRepository.getAverageAiScore(kpi.getEmployeeId(), startDate, endDate);
                yield avg != null ? avg : 0.0;
            }
            case "DAILY_REPORT_KPI_ALIGNMENT", "KPI_ALIGNMENT" -> {
                Double avg = dailyReportRepository.getAverageKpiAlignmentScore(kpi.getEmployeeId(), startDate, endDate);
                yield avg != null ? avg : 0.0;
            }
            default -> null;
        };
    }

    private boolean isAutoProgressEligible(String progressSource) {
        return progressSource != null && !progressSource.isBlank();
    }

    private void upsertWeeklyProgress(UUID kpiId, BigDecimal targetValue, Double achieved, Integer weekNumber, Integer year, UUID reportedBy) {
        Optional<WeeklyKpiProgress> existing = progressRepository.findByKpiIdAndWeekNumberAndYear(kpiId, weekNumber, year);
        if (existing.isPresent() && "manual".equalsIgnoreCase(existing.get().getSource())) {
            return;
        }

        WeeklyKpiProgress progress = existing.orElse(new WeeklyKpiProgress());
        progress.setKpiId(kpiId);
        progress.setReportedBy(reportedBy);
        progress.setWeekNumber(weekNumber);
        progress.setYear(year);
        progress.setAchievedValue(BigDecimal.valueOf(achieved));

        if (targetValue != null && targetValue.compareTo(BigDecimal.ZERO) > 0) {
            double pct = (achieved / targetValue.doubleValue()) * 100.0;
            progress.setProgressPercentage(BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP));
        }

        progress.setSource("auto");
        progress.setIngestedAt(OffsetDateTime.now());
        progressRepository.save(progress);
    }

    private void updateIndividualCurrentValue(IndividualKpi kpi, Integer year) {
        String progressSource = kpi.getProgressSource();
        if (!isAutoProgressEligible(progressSource)) {
            return;
        }

        Double value = null;
        if ("DAILY_REPORT_SCORE_AVG".equalsIgnoreCase(progressSource)
            || "DAILY_REPORT_KPI_ALIGNMENT".equalsIgnoreCase(progressSource)
            || "KPI_ALIGNMENT".equalsIgnoreCase(progressSource)) {
            List<WeeklyKpiProgress> progress = progressRepository
                .findByKpiIdAndYearOrderByWeekNumberDesc(kpi.getId(), year);
            if (!progress.isEmpty()) {
                value = progress.stream()
                    .map(p -> p.getAchievedValue() != null ? p.getAchievedValue().doubleValue() : null)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            }
        } else {
            value = progressRepository.sumAchievedValueByKpiIdAndYear(kpi.getId(), year);
        }

        if (value != null) {
            kpi.setCurrentValue(BigDecimal.valueOf(value));
        }
    }

    private String getQuarterForWeek(int weekNumber) {
        if (weekNumber <= 13) return "Q1";
        if (weekNumber <= 26) return "Q2";
        if (weekNumber <= 39) return "Q3";
        return "Q4";
    }

    private LocalDate getWeekStartDate(int weekNumber, int year) {
        return LocalDate.of(year, 1, 4)
            .with(WeekFields.ISO.weekOfYear(), weekNumber)
            .with(WeekFields.ISO.dayOfWeek(), 1);
    }
}
