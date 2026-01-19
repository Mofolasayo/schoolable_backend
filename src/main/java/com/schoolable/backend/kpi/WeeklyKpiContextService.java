package com.schoolable.backend.kpi;

import com.schoolable.backend.attendance.Attendance;
import com.schoolable.backend.attendance.AttendancePolicyService;
import com.schoolable.backend.attendance.AttendancePolicyService.AttendancePolicy;
import com.schoolable.backend.attendance.AttendanceRepository;
import com.schoolable.backend.performance.AutoAuraCalculationService;
import com.schoolable.backend.performance.DailyReport;
import com.schoolable.backend.performance.DailyReportRepository;
import com.schoolable.backend.performance.WeeklyReportRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.task.Task;
import com.schoolable.backend.task.TaskRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WeeklyKpiContextService {

    private static final String SUBJECT_EMPLOYEE = "EMPLOYEE";
    private static final String SUBJECT_TEAM = "TEAM";

    private final WeeklyKpiContextRepository contextRepository;
    private final ProfileRepository profileRepository;
    private final IndividualKpiRepository individualKpiRepository;
    private final TeamKpiRepository teamKpiRepository;
    private final WeeklyKpiProgressRepository progressRepository;
    private final DailyReportRepository dailyReportRepository;
    private final TaskRepository taskRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendancePolicyService attendancePolicyService;
    private final AutoAuraCalculationService autoAuraCalculationService;
    private final WeeklyReportRepository weeklyReportRepository;

    public WeeklyKpiContextService(
            WeeklyKpiContextRepository contextRepository,
            ProfileRepository profileRepository,
            IndividualKpiRepository individualKpiRepository,
            TeamKpiRepository teamKpiRepository,
            WeeklyKpiProgressRepository progressRepository,
            DailyReportRepository dailyReportRepository,
            TaskRepository taskRepository,
            AttendanceRepository attendanceRepository,
            AttendancePolicyService attendancePolicyService,
            AutoAuraCalculationService autoAuraCalculationService,
            WeeklyReportRepository weeklyReportRepository) {
        this.contextRepository = contextRepository;
        this.profileRepository = profileRepository;
        this.individualKpiRepository = individualKpiRepository;
        this.teamKpiRepository = teamKpiRepository;
        this.progressRepository = progressRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.taskRepository = taskRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendancePolicyService = attendancePolicyService;
        this.autoAuraCalculationService = autoAuraCalculationService;
        this.weeklyReportRepository = weeklyReportRepository;
    }

    public Optional<WeeklyKpiContext> getOrBuildEmployeeContext(UUID employeeId, int weekNumber, int year) {
        Optional<WeeklyKpiContext> existing = contextRepository
            .findBySubjectTypeAndSubjectIdAndWeekNumberAndYear(SUBJECT_EMPLOYEE, employeeId, weekNumber, year);
        if (existing.isPresent()) {
            return existing;
        }

        Profile profile = profileRepository.findById(employeeId).orElse(null);
        if (profile == null) {
            return Optional.empty();
        }

        WeeklyKpiContext context = buildEmployeeContext(profile, weekNumber, year);
        return Optional.of(contextRepository.save(context));
    }

    public Optional<WeeklyKpiContext> getOrBuildTeamContext(UUID teamLeadId, int weekNumber, int year) {
        Optional<WeeklyKpiContext> existing = contextRepository
            .findBySubjectTypeAndSubjectIdAndWeekNumberAndYear(SUBJECT_TEAM, teamLeadId, weekNumber, year);
        if (existing.isPresent()) {
            return existing;
        }

        Profile teamLead = profileRepository.findById(teamLeadId).orElse(null);
        if (teamLead == null || teamLead.getDepartment() == null) {
            return Optional.empty();
        }

        WeeklyKpiContext context = buildTeamContext(teamLead, weekNumber, year);
        return Optional.of(contextRepository.save(context));
    }

    private WeeklyKpiContext buildEmployeeContext(Profile profile, int weekNumber, int year) {
        String quarter = getQuarterForWeek(weekNumber);
        LocalDate weekStart = getWeekStart(weekNumber, year);
        LocalDate weekEnd = weekStart.plusDays(6);
        OffsetDateTime weekStartTime = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime weekEndTime = weekEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", profile.getId());
        context.put("employeeName", profile.getFullName());
        context.put("department", profile.getDepartment());
        context.put("weekNumber", weekNumber);
        context.put("year", year);
        context.put("quarter", quarter);

        Map<String, Object> kpiSummary = buildIndividualKpiSummary(profile.getId(), quarter, year);
        context.put("individualKpis", kpiSummary);

        Map<String, Object> reportSummary = buildDailyReportSummary(profile.getId(), weekStart, weekEnd);
        context.put("dailyReports", reportSummary);

        Map<String, Object> taskSummary = buildTaskSummary(profile.getId(), weekStartTime, weekEndTime);
        context.put("tasks", taskSummary);

        Map<String, Object> attendanceSummary = buildAttendanceSummary(profile.getId(), weekStart, weekEnd);
        context.put("attendance", attendanceSummary);

        Map<String, Object> aura = autoAuraCalculationService.calculateEmployeeScore(profile);
        context.put("auraScore", aura.getOrDefault("auraScore", 0));
        context.put("auraGrade", aura.getOrDefault("grade", "N/A"));

        String contextText = buildEmployeeContextText(profile, kpiSummary, reportSummary, taskSummary, attendanceSummary, aura);

        WeeklyKpiContext snapshot = new WeeklyKpiContext();
        snapshot.setSubjectType(SUBJECT_EMPLOYEE);
        snapshot.setSubjectId(profile.getId());
        snapshot.setDepartment(profile.getDepartment());
        snapshot.setWeekNumber(weekNumber);
        snapshot.setYear(year);
        snapshot.setQuarter(quarter);
        snapshot.setContextJson(context);
        snapshot.setContextText(contextText);
        return snapshot;
    }

    private WeeklyKpiContext buildTeamContext(Profile teamLead, int weekNumber, int year) {
        String quarter = getQuarterForWeek(weekNumber);
        LocalDate weekStart = getWeekStart(weekNumber, year);
        LocalDate weekEnd = weekStart.plusDays(6);
        OffsetDateTime weekStartTime = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime weekEndTime = weekEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        String department = teamLead.getDepartment();
        List<Profile> members = profileRepository.findByDepartment(department).stream()
            .filter(profile -> profile.getStatus() == null
                || profile.getStatus().isBlank()
                || "active".equalsIgnoreCase(profile.getStatus())
                || "pending".equalsIgnoreCase(profile.getStatus())
                || "probation".equalsIgnoreCase(profile.getStatus()))
            .toList();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("teamLeadId", teamLead.getId());
        context.put("teamLeadName", teamLead.getFullName());
        context.put("department", department);
        context.put("teamSize", members.size());
        context.put("weekNumber", weekNumber);
        context.put("year", year);
        context.put("quarter", quarter);

        Map<String, Object> kpiSummary = buildTeamKpiSummary(department, quarter, year);
        context.put("teamKpis", kpiSummary);

        Map<String, Object> reportSummary = buildDepartmentDailyReportSummary(department, weekStart, weekEnd);
        context.put("dailyReports", reportSummary);

        Map<String, Object> taskSummary = buildTeamTaskSummary(members, weekStartTime, weekEndTime);
        context.put("tasks", taskSummary);

        Map<String, Object> attendanceSummary = buildTeamAttendanceSummary(members, weekStart, weekEnd);
        context.put("attendance", attendanceSummary);

        Map<String, Object> auraSummary = buildTeamAuraSummary(members);
        context.put("aura", auraSummary);

        long weeklyReportsSubmitted = weeklyReportRepository.findByWeekNumberAndYearOrderByCreatedAtDesc(weekNumber, year)
            .stream()
            .filter(report -> {
                UUID employeeId = report.getEmployeeId();
                return members.stream().anyMatch(member -> member.getId().equals(employeeId));
            })
            .count();
        context.put("weeklyReportsSubmitted", weeklyReportsSubmitted);

        String contextText = buildTeamContextText(teamLead, kpiSummary, reportSummary, taskSummary, attendanceSummary, auraSummary, weeklyReportsSubmitted);

        WeeklyKpiContext snapshot = new WeeklyKpiContext();
        snapshot.setSubjectType(SUBJECT_TEAM);
        snapshot.setSubjectId(teamLead.getId());
        snapshot.setDepartment(department);
        snapshot.setWeekNumber(weekNumber);
        snapshot.setYear(year);
        snapshot.setQuarter(quarter);
        snapshot.setContextJson(context);
        snapshot.setContextText(contextText);
        return snapshot;
    }

    private Map<String, Object> buildIndividualKpiSummary(UUID employeeId, String quarter, int year) {
        List<IndividualKpi> kpis = individualKpiRepository.findActiveByEmployeeAndPeriod(employeeId, quarter, year);
        List<Map<String, Object>> items = new ArrayList<>();

        double weightedAchievement = 0;
        double totalWeight = 0;
        for (IndividualKpi kpi : kpis) {
            double achievement = resolveAchievement(kpi);
            Integer weight = kpi.getWeight() != null ? kpi.getWeight() : 0;
            totalWeight += weight;
            weightedAchievement += achievement * weight;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", kpi.getName());
            item.put("targetValue", kpi.getTargetValue());
            item.put("currentValue", kpi.getCurrentValue());
            item.put("unit", kpi.getTargetUnit());
            item.put("weight", weight);
            item.put("achievement", round1(achievement));
            items.add(item);
        }

        double weighted = totalWeight > 0 ? weightedAchievement / totalWeight : 0;
        return Map.of(
            "count", items.size(),
            "weightedAchievement", round1(weighted),
            "items", items
        );
    }

    private Map<String, Object> buildTeamKpiSummary(String department, String quarter, int year) {
        List<TeamKpi> kpis = teamKpiRepository.findByDepartmentAndQuarterAndYearAndIsActiveTrue(department, quarter, year);
        List<Map<String, Object>> items = new ArrayList<>();

        for (TeamKpi kpi : kpis) {
            Double achieved = progressRepository.sumAchievedValueByKpiIdAndYear(kpi.getId(), year);
            double achievedValue = achieved != null ? achieved : 0;
            double progressPct = kpi.getTargetValue() != null && kpi.getTargetValue().doubleValue() > 0
                ? (achievedValue / kpi.getTargetValue().doubleValue()) * 100
                : 0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", kpi.getName());
            item.put("weight", kpi.getWeight());
            item.put("targetValue", kpi.getTargetValue());
            item.put("unit", kpi.getTargetUnit());
            item.put("achievedValue", round1(achievedValue));
            item.put("progressPct", round1(progressPct));
            items.add(item);
        }

        return Map.of(
            "count", items.size(),
            "items", items
        );
    }

    private Map<String, Object> buildDailyReportSummary(UUID employeeId, LocalDate weekStart, LocalDate weekEnd) {
        List<DailyReport> reports = dailyReportRepository.findByEmployeeAndDateRange(employeeId, weekStart, weekEnd);
        double avgAiScore = reports.stream()
            .filter(r -> r.getAiScore() != null)
            .mapToDouble(r -> r.getAiScore().doubleValue())
            .average()
            .orElse(0.0);
        double avgKpiAlignment = reports.stream()
            .filter(r -> r.getKpiAlignmentScore() != null)
            .mapToDouble(r -> r.getKpiAlignmentScore().doubleValue())
            .average()
            .orElse(0.0);

        return Map.of(
            "submittedCount", reports.size(),
            "averageAiScore", round1(avgAiScore),
            "averageKpiAlignment", round1(avgKpiAlignment)
        );
    }

    private Map<String, Object> buildDepartmentDailyReportSummary(String department, LocalDate weekStart, LocalDate weekEnd) {
        List<DailyReport> reports = dailyReportRepository.findByDepartmentAndDateRange(department, weekStart, weekEnd);
        double avgAiScore = reports.stream()
            .filter(r -> r.getAiScore() != null)
            .mapToDouble(r -> r.getAiScore().doubleValue())
            .average()
            .orElse(0.0);
        double avgKpiAlignment = reports.stream()
            .filter(r -> r.getKpiAlignmentScore() != null)
            .mapToDouble(r -> r.getKpiAlignmentScore().doubleValue())
            .average()
            .orElse(0.0);

        return Map.of(
            "submittedCount", reports.size(),
            "averageAiScore", round1(avgAiScore),
            "averageKpiAlignment", round1(avgKpiAlignment)
        );
    }

    private Map<String, Object> buildTaskSummary(UUID employeeId, OffsetDateTime weekStart, OffsetDateTime weekEndExclusive) {
        List<Task> tasks = taskRepository.findByAssigneeIdAndCreatedAtAfterOrderByCreatedAtDesc(employeeId, weekStart);
        List<Task> weekTasks = tasks.stream()
            .filter(task -> task.getCreatedAt() != null)
            .filter(task -> !task.getCreatedAt().isAfter(weekEndExclusive))
            .toList();

        long completed = weekTasks.stream().filter(this::isTaskDone).count();
        long withDueDates = weekTasks.stream().filter(task -> task.getDueDate() != null).count();
        long onTime = weekTasks.stream()
            .filter(this::isTaskDone)
            .filter(task -> task.getDueDate() != null && task.getUpdatedAt() != null)
            .filter(task -> !task.getUpdatedAt().isAfter(task.getDueDate()))
            .count();
        double onTimeRate = withDueDates > 0 ? (onTime * 100.0) / withDueDates : 0.0;

        Double avgRating = taskRepository.getAverageQualityRatingAfter(employeeId, weekStart);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", weekTasks.size());
        summary.put("completed", completed);
        summary.put("onTimeRate", round1(onTimeRate));
        summary.put("averageRating", avgRating != null ? round1(avgRating) : null);
        return summary;
    }

    private Map<String, Object> buildTeamTaskSummary(List<Profile> members, OffsetDateTime weekStart, OffsetDateTime weekEndExclusive) {
        long totalTasks = 0;
        long completedTasks = 0;
        long onTimeTasks = 0;
        long withDueDates = 0;
        double ratingSum = 0.0;
        long ratingCount = 0;

        for (Profile member : members) {
            List<Task> tasks = taskRepository.findByAssigneeIdAndCreatedAtAfterOrderByCreatedAtDesc(member.getId(), weekStart);
            List<Task> weekTasks = tasks.stream()
                .filter(task -> task.getCreatedAt() != null)
                .filter(task -> !task.getCreatedAt().isAfter(weekEndExclusive))
                .toList();

            totalTasks += weekTasks.size();
            long memberCompleted = weekTasks.stream().filter(this::isTaskDone).count();
            completedTasks += memberCompleted;

            long memberWithDue = weekTasks.stream().filter(task -> task.getDueDate() != null).count();
            withDueDates += memberWithDue;
            onTimeTasks += weekTasks.stream()
                .filter(this::isTaskDone)
                .filter(task -> task.getDueDate() != null && task.getUpdatedAt() != null)
                .filter(task -> !task.getUpdatedAt().isAfter(task.getDueDate()))
                .count();

            Double avgRating = taskRepository.getAverageQualityRatingAfter(member.getId(), weekStart);
            if (avgRating != null) {
                ratingSum += avgRating;
                ratingCount++;
            }
        }

        double onTimeRate = withDueDates > 0 ? (onTimeTasks * 100.0) / withDueDates : 0.0;
        Double avgRating = ratingCount > 0 ? ratingSum / ratingCount : null;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", totalTasks);
        summary.put("completed", completedTasks);
        summary.put("onTimeRate", round1(onTimeRate));
        summary.put("averageRating", avgRating != null ? round1(avgRating) : null);
        return summary;
    }

    private Map<String, Object> buildAttendanceSummary(UUID employeeId, LocalDate weekStart, LocalDate weekEnd) {
        List<Attendance> attendance = attendanceRepository.findByUserIdOrderByDateDesc(employeeId);
        List<Attendance> weekAttendance = attendance.stream()
            .filter(record -> record.getDate() != null
                && !record.getDate().isBefore(weekStart)
                && !record.getDate().isAfter(weekEnd))
            .toList();

        int expectedDays = 0;
        int presentDays = 0;
        int onTimeDays = 0;
        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            AttendancePolicy policy = attendancePolicyService.resolvePolicy(employeeId, currentDate);
            if (!policy.isWorkDay() || policy.isHoliday() || policy.isOnLeave()) {
                continue;
            }
            expectedDays++;
            Attendance record = weekAttendance.stream()
                .filter(a -> currentDate.equals(a.getDate()))
                .findFirst()
                .orElse(null);
            if (record != null && record.getCheckIn() != null) {
                presentDays++;
                if (!attendancePolicyService.evaluateCheckIn(record.getCheckIn().toLocalTime(), policy.schedule()).isLate()) {
                    onTimeDays++;
                }
            }
        }

        double attendanceRate = expectedDays > 0 ? (presentDays * 100.0) / expectedDays : 0.0;
        double onTimeRate = presentDays > 0 ? (onTimeDays * 100.0) / presentDays : 0.0;

        return Map.of(
            "expectedDays", expectedDays,
            "presentDays", presentDays,
            "attendanceRate", round1(attendanceRate),
            "onTimeRate", round1(onTimeRate)
        );
    }

    private Map<String, Object> buildTeamAttendanceSummary(List<Profile> members, LocalDate weekStart, LocalDate weekEnd) {
        int expectedDays = 0;
        int presentDays = 0;

        for (Profile member : members) {
            Map<String, Object> summary = buildAttendanceSummary(member.getId(), weekStart, weekEnd);
            expectedDays += ((Number) summary.get("expectedDays")).intValue();
            presentDays += ((Number) summary.get("presentDays")).intValue();
        }

        double attendanceRate = expectedDays > 0 ? (presentDays * 100.0) / expectedDays : 0.0;
        return Map.of(
            "expectedDays", expectedDays,
            "presentDays", presentDays,
            "attendanceRate", round1(attendanceRate)
        );
    }

    private Map<String, Object> buildTeamAuraSummary(List<Profile> members) {
        double sum = 0.0;
        int count = 0;
        int atRisk = 0;
        for (Profile member : members) {
            Map<String, Object> aura = autoAuraCalculationService.calculateEmployeeScore(member);
            Object scoreObj = aura.get("auraScore");
            double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
            sum += score;
            count++;
            if (score > 0 && score < 60) {
                atRisk++;
            }
        }
        double avg = count > 0 ? sum / count : 0.0;
        return Map.of(
            "averageScore", round1(avg),
            "atRiskCount", atRisk
        );
    }

    private String buildEmployeeContextText(
            Profile profile,
            Map<String, Object> kpiSummary,
            Map<String, Object> reportSummary,
            Map<String, Object> taskSummary,
            Map<String, Object> attendanceSummary,
            Map<String, Object> auraSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Employee KPI snapshot for ").append(profile.getFullName()).append(". ");
        sb.append("Weighted KPI achievement: ").append(kpiSummary.get("weightedAchievement")).append("%. ");
        sb.append("Daily reports submitted: ").append(reportSummary.get("submittedCount"));
        sb.append(" with avg score ").append(reportSummary.get("averageAiScore")).append("%. ");
        sb.append("Avg KPI alignment ").append(reportSummary.get("averageKpiAlignment")).append("%. ");
        sb.append("Tasks completed ").append(taskSummary.get("completed")).append(" of ").append(taskSummary.get("total")).append(". ");
        sb.append("On-time rate ").append(taskSummary.get("onTimeRate")).append("%. ");
        sb.append("Attendance rate ").append(attendanceSummary.get("attendanceRate")).append("%, ");
        sb.append("on-time check-ins ").append(attendanceSummary.get("onTimeRate")).append("%. ");
        sb.append("Aura score ").append(auraSummary.getOrDefault("auraScore", 0)).append(".");
        return sb.toString();
    }

    private String buildTeamContextText(
            Profile teamLead,
            Map<String, Object> kpiSummary,
            Map<String, Object> reportSummary,
            Map<String, Object> taskSummary,
            Map<String, Object> attendanceSummary,
            Map<String, Object> auraSummary,
            long weeklyReportsSubmitted) {
        StringBuilder sb = new StringBuilder();
        sb.append("Team KPI snapshot for ").append(teamLead.getDepartment()).append(". ");
        sb.append("Team KPIs tracked: ").append(kpiSummary.get("count")).append(". ");
        sb.append("Daily reports submitted: ").append(reportSummary.get("submittedCount"));
        sb.append(" with avg score ").append(reportSummary.get("averageAiScore")).append("%. ");
        sb.append("Avg KPI alignment ").append(reportSummary.get("averageKpiAlignment")).append("%. ");
        sb.append("Tasks completed ").append(taskSummary.get("completed")).append(" of ").append(taskSummary.get("total")).append(". ");
        sb.append("On-time rate ").append(taskSummary.get("onTimeRate")).append("%. ");
        sb.append("Attendance rate ").append(attendanceSummary.get("attendanceRate")).append("%. ");
        sb.append("Avg Aura score ").append(auraSummary.get("averageScore")).append(". ");
        sb.append("Weekly reports submitted: ").append(weeklyReportsSubmitted).append(".");
        return sb.toString();
    }

    private boolean isTaskDone(Task task) {
        if (task == null || task.getStatus() == null) {
            return false;
        }
        String status = task.getStatus().trim().toLowerCase(Locale.ROOT);
        return status.equals("completed") || status.equals("done");
    }

    private double resolveAchievement(IndividualKpi kpi) {
        if (kpi.getAchievementPercentage() != null) {
            return kpi.getAchievementPercentage().doubleValue();
        }
        BigDecimal current = kpi.getCurrentValue();
        BigDecimal target = kpi.getTargetValue();
        if (current != null && target != null && target.doubleValue() > 0) {
            return (current.doubleValue() / target.doubleValue()) * 100;
        }
        return 0.0;
    }

    private String getQuarterForWeek(int weekNumber) {
        if (weekNumber <= 13) return "Q1";
        if (weekNumber <= 26) return "Q2";
        if (weekNumber <= 39) return "Q3";
        return "Q4";
    }

    private LocalDate getWeekStart(int weekNumber, int year) {
        WeekFields weekFields = WeekFields.ISO;
        return LocalDate.of(year, 1, 4)
            .with(weekFields.weekOfYear(), weekNumber)
            .with(weekFields.dayOfWeek(), 1);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
