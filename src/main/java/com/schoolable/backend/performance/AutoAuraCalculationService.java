package com.schoolable.backend.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.attendance.Attendance;
import com.schoolable.backend.attendance.AttendancePolicyService;
import com.schoolable.backend.attendance.AttendanceRepository;
import com.schoolable.backend.attendance.WorkSchedule;
import com.schoolable.backend.compliance.ComplianceSubmissionRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.task.Task;
import com.schoolable.backend.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Supplier;

/**
 * FULLY AUTOMATED AURA CALCULATION SERVICE
 * 
 * This service calculates performance scores using ONLY system data.
 * Team Lead ratings are only used for subjective metrics that cannot be auto-calculated.
 * 
 * AUTOMATION BREAKDOWN:
 * =====================
 * 
 * 1. TASK METRICS (from Task entity):
 *    - task_completion_rate: Completed / Total assigned
 *    - on_time_delivery: Completed on/before due date / Total completed
 *    - task_quality: Tasks not reopened / Total completed (future: track status changes)
 *    - documentation: Tasks with attachments or notes / Total
 *    - workload_handling: Tasks handled vs average
 * 
 * 2. ATTENDANCE METRICS (from Attendance entity):
 *    - attendance_rate: Days present / Expected work days
 *    - punctuality: Check-in before 9 AM / Total check-ins
 *    - consistency: Low variance in check-in times
 * 
 * 3. COMPLIANCE METRICS (from ComplianceSubmission entity):
 *    - policy_compliance: Compliant submissions / Required submissions
 *    - process_adherence: SOPs followed
 * 
 * 4. TRAINING METRICS (from TrainingRecord entity):
 *    - training_completion: Completed trainings / Required trainings
 *    - certifications: Certificates uploaded this quarter
 *    - training_hours: Hours in learning modules
 * 
 * 5. DERIVED METRICS (calculated from historical data):
 *    - improvement_trend: Current quarter score - Previous quarter
 *    - collaboration: Cross-team task participation
 * 
 * 6. TEAM LEAD RATINGS (from WeeklyPerformanceReport - MINIMAL):
 *    - initiative, attitude, professionalism (soft skills only)
 */
@Service
public class AutoAuraCalculationService {

    private static final Logger log = LoggerFactory.getLogger(AutoAuraCalculationService.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ComplianceSubmissionRepository complianceRepository;

    @Autowired
    private TrainingRecordRepository trainingRepository;

    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AttendancePolicyService attendancePolicyService;

    @Autowired(required = false)
    private SubMetricScoreRepository subMetricScoreRepository;

    @Autowired
    private PeerHelpfulnessRepository peerHelpfulnessRepository;

    @Autowired(required = false)
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    // SCHEDULED AUTO-CALCULATION
    // Runs every Sunday at 2 AM to calculate weekly scores
    // ============================================================

    @Scheduled(cron = "0 0 2 * * SUN")
    public void calculateAllEmployeeScores() {
        log.info("Starting weekly auto-Aura calculation");
        
        List<Profile> allEmployees = profileRepository.findAll();
        int processed = 0;
        int errors = 0;

        for (Profile employee : allEmployees) {
            try {
                calculateAndSaveEmployeeScore(employee);
                processed++;
            } catch (Exception e) {
                log.warn("Error calculating score for {}: {}", employee.getId(), e.getMessage());
                errors++;
            }
        }

        log.info("Auto-Aura calculation complete. Processed: {}, Errors: {}", processed, errors);
    }

    // ============================================================
    // MAIN CALCULATION METHOD
    // ============================================================

    public Map<String, Object> calculateEmployeeScore(UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId).orElse(null);
        if (profile == null) {
            return Map.of("error", "Employee not found");
        }
        return calculateEmployeeScore(profile);
    }

    public Map<String, Object> calculateEmployeeScore(Profile profile) {
        String department = profile.getDepartment();
        DepartmentKpiConfig.DepartmentProfile kpiProfile = 
            DepartmentKpiConfig.getProfileForDepartment(department);

        LocalDate quarterStart = getQuarterStart();
        LocalDate now = LocalDate.now();

        // ============ ONBOARDING GRACE PERIOD ============
        // New employees (< 30 days) get a gradual ramp-up
        // This prevents 0% scores for employees who haven't had time to generate data
        double onboardingFactor = 1.0; // 1.0 = full weighting, no grace
        boolean isOnboarding = false;
        long daysEmployed = 0;
        
        if (profile.getDateJoined() != null) {
            daysEmployed = ChronoUnit.DAYS.between(
                profile.getDateJoined().toLocalDate(), now);
            
            if (daysEmployed < 30) {
                isOnboarding = true;
                // Linear ramp: Day 0 = 0.3, Day 30 = 1.0
                onboardingFactor = 0.3 + (0.7 * daysEmployed / 30.0);
            }
        }
        // =================================================

        Map<String, Object> result = new HashMap<>();
        Map<String, Map<String, Object>> pillarResults = new HashMap<>();
        double totalScore = 0.0;
        CalculationContext context = new CalculationContext(profile, quarterStart, now);
        String quarter = getQuarterForDate(now);

        // Calculate each pillar
        for (Map.Entry<String, DepartmentKpiConfig.PillarProfile> pillarEntry : kpiProfile.pillars.entrySet()) {
            String pillarKey = pillarEntry.getKey();
            DepartmentKpiConfig.PillarProfile pillarConfig = pillarEntry.getValue();

            Map<String, Object> pillarResult = calculatePillar(
                profile, pillarKey, pillarConfig, quarterStart, now, context
            );

            double pillarScore = (double) pillarResult.get("score");
            
            // Apply onboarding grace: missing data metrics get a baseline
            if (isOnboarding && pillarScore == 0.0) {
                // Give new employees a neutral 50% for metrics without data yet
                pillarScore = 50.0;
                pillarResult.put("score", pillarScore);
                pillarResult.put("onboardingAdjusted", true);
            }
            
            double pillarWeight = pillarConfig.weight / 100.0;
            double contribution = pillarScore * pillarWeight;

            pillarResult.put("weight", pillarConfig.weight);
            pillarResult.put("contribution", contribution);
            pillarResults.put(pillarKey, pillarResult);

            totalScore += contribution;
        }

        // Build response
        result.put("employeeId", profile.getId().toString());
        result.put("employeeName", profile.getFullName());
        result.put("department", department);
        result.put("departmentProfile", kpiProfile.displayName);
        result.put("auraScore", Math.round(totalScore * 10.0) / 10.0);
        result.put("grade", calculateGrade(totalScore));
        result.put("qgpa", calculateQgpa(totalScore));
        result.put("pillars", pillarResults);
        result.put("quarterStart", quarterStart.toString());
        result.put("quarter", quarter);
        result.put("year", now.getYear());
        result.put("automationRate", kpiProfile.getAutomationPercentage());
        result.put("calculatedAt", LocalDateTime.now().toString());
        
        // Add onboarding metadata to response
        if (isOnboarding) {
            result.put("isOnboarding", true);
            result.put("daysEmployed", daysEmployed);
            result.put("onboardingDaysRemaining", 30 - daysEmployed);
            result.put("onboardingMessage", 
                "You're in your onboarding period (" + daysEmployed + "/30 days). " +
                "Scores are adjusted while you build your performance history.");
        }

        return result;
    }

    // ============================================================
    // PILLAR CALCULATION
    // ============================================================

    private Map<String, Object> calculatePillar(
            Profile profile,
            String pillarKey,
            DepartmentKpiConfig.PillarProfile pillarConfig,
            LocalDate quarterStart,
            LocalDate now,
            CalculationContext context) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> subMetrics = new ArrayList<>();
        double pillarScore = 0.0;
        int autoCount = 0;
        int manualCount = 0;

        for (Map.Entry<String, DepartmentKpiConfig.MetricConfig> metricEntry : pillarConfig.metrics.entrySet()) {
            String metricKey = metricEntry.getKey();
            DepartmentKpiConfig.MetricConfig metricConfig = metricEntry.getValue();

            // Calculate the metric value
            MetricResult metricResult = calculateMetric(profile, metricKey, metricConfig, quarterStart, now, context);
            double metricScore = metricResult.score;
            double weightedScore = metricScore * (metricConfig.weightInPillar / 100.0);
            pillarScore += weightedScore;

            boolean isAuto = "auto".equals(metricConfig.source);
            if (isAuto) autoCount++; else manualCount++;

            Map<String, Object> subMetric = new LinkedHashMap<>();
            subMetric.put("key", metricKey);
            subMetric.put("displayName", metricConfig.displayName);
            subMetric.put("score", Math.round(metricScore * 10.0) / 10.0);
            subMetric.put("source", metricConfig.source);
            subMetric.put("dataSource", metricConfig.dataSource);
            subMetric.put("weightInPillar", metricConfig.weightInPillar);
            subMetric.put("contribution", Math.round(weightedScore * 10.0) / 10.0);
            if (metricResult.rawData != null && !metricResult.rawData.isEmpty()) {
                subMetric.put("rawData", metricResult.rawData);
            }
            subMetrics.add(subMetric);
        }

        result.put("name", formatPillarName(pillarKey));
        result.put("score", Math.round(pillarScore * 10.0) / 10.0);
        result.put("subMetrics", subMetrics);
        result.put("autoCalculatedCount", autoCount);
        result.put("manualRatingCount", manualCount);
        result.put("dataSource", autoCount > manualCount ? "auto" : (autoCount == 0 ? "team_lead" : "mixed"));

        return result;
    }

    // ============================================================
    // INDIVIDUAL METRIC CALCULATIONS
    // ============================================================

    private MetricResult calculateMetric(
            Profile profile,
            String metricKey,
            DepartmentKpiConfig.MetricConfig metricConfig,
            LocalDate quarterStart,
            LocalDate now,
            CalculationContext context) {

        UUID employeeId = profile.getId();

        try {
            switch (metricConfig.dataSource) {
                case "tasks":
                    return calculateTaskMetric(profile, metricKey, metricConfig, quarterStart, now, context);
                case "attendance":
                    return calculateAttendanceMetric(profile, metricKey, metricConfig, quarterStart, now, context);
                case "compliance":
                    return calculateComplianceMetric(employeeId, metricKey, metricConfig, quarterStart, now);
                case "training":
                    return calculateTrainingMetric(employeeId, metricKey, metricConfig, quarterStart, now);
                case "daily_reports":
                    return calculateDailyReportMetric(employeeId, metricKey, metricConfig, quarterStart, now, context);
                case "weekly_report":
                    return calculateTeamLeadRating(employeeId, metricKey, metricConfig, quarterStart);
                case "aura":
                    return calculateHistoricalMetric(employeeId, metricKey, metricConfig);
                default:
                    return MetricResult.of(0.0, Map.of("reason", "unsupported_data_source"));
            }
        } catch (Exception e) {
            log.warn("Error calculating {}: {}", metricKey, e.getMessage());
            return MetricResult.of(0.0, Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // TASK METRICS (100% Automated)
    // ============================================================

    private MetricResult calculateTaskMetric(Profile profile, String metricKey,
            DepartmentKpiConfig.MetricConfig config, LocalDate quarterStart, LocalDate now, CalculationContext context) {

        TaskContext taskContext = getTaskContext(context);
        List<Task> quarterTasks = taskContext.tasks;

        Map<String, Long> statusBreakdown = buildStatusBreakdown(quarterTasks);
        List<Task> activeTasks = quarterTasks.stream()
            .filter(task -> normalizeTaskStatus(task.getStatus()) != Task.TaskStatus.CANCELLED)
            .toList();
        long cancelled = quarterTasks.size() - activeTasks.size();

        if (quarterTasks.isEmpty()) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("totalTasks", 0);
            raw.put("reason", "no_tasks");
            return MetricResult.of(0.0, raw);
        }

        switch (metricKey) {
            case "task_completion_rate":
            case "task_completion": {
                long completed = activeTasks.stream().filter(this::isTaskDone).count();
                double score = activeTasks.isEmpty() ? 0.0 : (completed * 100.0) / activeTasks.size();
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("totalTasks", activeTasks.size());
                raw.put("completedTasks", completed);
                raw.put("cancelledTasks", cancelled);
                raw.put("statusBreakdown", statusBreakdown);
                return MetricResult.of(score, raw);
            }

            case "on_time_delivery":
            case "deadline_adherence": {
                List<Task> completedTasks = activeTasks.stream().filter(this::isTaskDone).toList();
                long withDueDates = completedTasks.stream().filter(t -> t.getDueDate() != null).count();
                long onTime = completedTasks.stream()
                    .filter(t -> t.getDueDate() != null && t.getUpdatedAt() != null)
                    .filter(t -> !t.getUpdatedAt().isAfter(t.getDueDate()))
                    .count();
                double score = withDueDates > 0 ? (onTime * 100.0) / withDueDates : 0.0;

                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("completedTasks", completedTasks.size());
                raw.put("completedWithDueDate", withDueDates);
                raw.put("onTime", onTime);
                return MetricResult.of(score, raw);
            }

            case "task_quality": {
                Double avgRating = taskRepository.getAverageQualityRatingAfter(
                    profile.getId(), quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC));
                long ratedCount = taskRepository.countRatedTasksAfter(
                    profile.getId(), quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC));
                double score = avgRating != null ? (avgRating / 5.0) * 100 : 0.0;

                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("ratedTasks", ratedCount);
                raw.put("averageRating", avgRating);
                return MetricResult.of(score, raw);
            }

            case "documentation": {
                long documented = activeTasks.stream()
                    .filter(t -> t.getDescription() != null && t.getDescription().length() > 20)
                    .count();
                double score = activeTasks.isEmpty() ? 0.0 : (documented * 100.0) / activeTasks.size();
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("documentedTasks", documented);
                raw.put("totalTasks", activeTasks.size());
                return MetricResult.of(score, raw);
            }

            case "workload_handling":
            case "capacity": {
                double expectedTasks = taskContext.expectedTasks;
                double score = expectedTasks > 0 ? Math.min(100, (activeTasks.size() / expectedTasks) * 100) : 0.0;
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("expectedTasks", expectedTasks);
                raw.put("actualTasks", activeTasks.size());
                return MetricResult.of(score, raw);
            }

            case "campaign_delivery":
            case "content_output": {
                long completed = activeTasks.stream().filter(this::isTaskDone).count();
                double score = activeTasks.isEmpty() ? 0.0 : (completed * 100.0) / activeTasks.size();
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("totalTasks", activeTasks.size());
                raw.put("completedTasks", completed);
                return MetricResult.of(score, raw);
            }

            case "team_support":
            case "collaboration":
            case "team_collaboration": {
                int currentYear = now.getYear();
                int startWeek = getQuarterStartWeek(quarterStart);
                int endWeek = now.get(WeekFields.ISO.weekOfYear());

                Double peerAvg = peerHelpfulnessRepository.getAverageRatingForPeriod(
                    profile.getId(), currentYear, startWeek, endWeek);

                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("peerAverageRating", peerAvg);

                if (peerAvg != null) {
                    return MetricResult.of((peerAvg / 5.0) * 100, raw);
                }

                long supportTasks = activeTasks.stream()
                    .filter(this::isTaskDone)
                    .filter(t -> t.getCreatedBy() != null && !t.getCreatedBy().equals(profile.getId()))
                    .count();
                raw.put("supportTasks", supportTasks);
                double score = supportTasks > 0 ? Math.min(100, (supportTasks / 5.0) * 100) : 0.0;
                return MetricResult.of(score, raw);
            }

            case "employee_support": {
                long hrTasks = activeTasks.stream().filter(this::isTaskDone).count();
                double score = hrTasks >= 5 ? 100.0 : (hrTasks * 20.0);
                return MetricResult.of(score, Map.of("completedTasks", hrTasks));
            }

            case "accuracy":
                return calculateTaskMetric(profile, "task_quality", config, quarterStart, now, context);

            case "response_time":
            case "client_responsiveness": {
                List<Task> tasksWithResponse = activeTasks.stream()
                    .filter(t -> t.getFirstResponseAt() != null && t.getCreatedAt() != null)
                    .toList();

                if (tasksWithResponse.isEmpty()) {
                    return MetricResult.of(0.0, Map.of("tasksWithResponse", 0));
                }

                double avgHours = tasksWithResponse.stream()
                    .mapToDouble(t -> {
                        long hours = ChronoUnit.HOURS.between(t.getCreatedAt(), t.getFirstResponseAt());
                        return Math.max(0, hours);
                    })
                    .average()
                    .orElse(0);

                double targetHours = config.target > 0 ? config.target : 4.0;
                double score;
                if (avgHours <= targetHours) score = 100.0;
                else if (avgHours >= 24) score = 20.0;
                else score = Math.max(20, 100 - ((avgHours - targetHours) / (24 - targetHours)) * 80);

                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("averageResponseHours", avgHours);
                raw.put("targetHours", targetHours);
                raw.put("tasksWithResponse", tasksWithResponse.size());
                return MetricResult.of(score, raw);
            }

            default:
                return calculateTaskMetric(profile, "task_completion_rate", config, quarterStart, now, context);
        }
    }

    // ============================================================
    // ATTENDANCE METRICS (100% Automated)
    // ============================================================

    private MetricResult calculateAttendanceMetric(Profile profile, String metricKey,
            DepartmentKpiConfig.MetricConfig config, LocalDate quarterStart, LocalDate now, CalculationContext context) {

        AttendanceContext attendanceContext = getAttendanceContext(context);
        int expectedWorkDays = attendanceContext.expectedWorkDays;
        List<Attendance> quarterAttendances = attendanceContext.attendances;

        switch (metricKey) {
            case "attendance_punctuality": {
                if (expectedWorkDays == 0) {
                    return MetricResult.of(100.0, Map.of("expectedWorkDays", 0));
                }

                int checkedInDays = 0;
                int onTimeDays = 0;
                int lateDays = 0;
                int totalMinutesLate = 0;

                for (Attendance att : quarterAttendances) {
                    if (att.getDate() == null || att.getCheckIn() == null) {
                        continue;
                    }
                    AttendancePolicyService.AttendancePolicy policy = attendanceContext.policyByDate.get(att.getDate());
                    if (policy == null || !policy.isWorkDay() || policy.isHoliday() || policy.isOnLeave()) {
                        continue;
                    }
                    WorkSchedule schedule = policy.schedule();
                    if (schedule == null || schedule.getStartTime() == null) {
                        continue;
                    }

                    checkedInDays++;
                    LocalTime checkInLocal = resolveCheckInTime(att, schedule);
                    LocalTime deadline = schedule.getStartTime().plusMinutes(
                        schedule.getGraceMinutes() != null ? schedule.getGraceMinutes() : 0
                    );
                    if (checkInLocal.isAfter(deadline)) {
                        lateDays++;
                        totalMinutesLate += (int) Duration.between(deadline, checkInLocal).toMinutes();
                    } else {
                        onTimeDays++;
                    }
                }

                double attendanceRate = expectedWorkDays > 0 ? (checkedInDays * 100.0) / expectedWorkDays : 0.0;
                double punctualityRate = checkedInDays > 0 ? (onTimeDays * 100.0) / checkedInDays : 0.0;
                double score = (attendanceRate * 0.6) + (punctualityRate * 0.4);

                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("expectedWorkDays", expectedWorkDays);
                raw.put("checkedInDays", checkedInDays);
                raw.put("onTimeDays", onTimeDays);
                raw.put("lateDays", lateDays);
                raw.put("avgMinutesLate", lateDays > 0 ? (double) totalMinutesLate / lateDays : 0.0);
                raw.put("attendanceRate", attendanceRate);
                raw.put("punctualityRate", punctualityRate);
                return MetricResult.of(score, raw);
            }

            case "attendance_rate":
            case "attendance": {
                if (expectedWorkDays == 0) {
                    return MetricResult.of(100.0, Map.of("expectedWorkDays", 0));
                }
                int checkedInDays = (int) quarterAttendances.stream()
                    .filter(a -> a.getDate() != null && a.getCheckIn() != null)
                    .filter(a -> attendanceContext.expectedDates.contains(a.getDate()))
                    .count();
                double score = Math.min(100, (checkedInDays * 100.0) / expectedWorkDays);
                return MetricResult.of(score, Map.of(
                    "expectedWorkDays", expectedWorkDays,
                    "checkedInDays", checkedInDays
                ));
            }

            case "punctuality": {
                long onTime = quarterAttendances.stream()
                    .filter(a -> a.getDate() != null && a.getCheckIn() != null)
                    .filter(a -> attendanceContext.expectedDates.contains(a.getDate()))
                    .filter(a -> {
                        AttendancePolicyService.AttendancePolicy policy = attendanceContext.policyByDate.get(a.getDate());
                        if (policy == null || policy.schedule() == null || policy.schedule().getStartTime() == null) {
                            return false;
                        }
                        LocalTime checkInLocal = resolveCheckInTime(a, policy.schedule());
                        LocalTime deadline = policy.schedule().getStartTime().plusMinutes(
                            policy.schedule().getGraceMinutes() != null ? policy.schedule().getGraceMinutes() : 0
                        );
                        return !checkInLocal.isAfter(deadline);
                    })
                    .count();

                long total = quarterAttendances.stream()
                    .filter(a -> a.getDate() != null && a.getCheckIn() != null)
                    .filter(a -> attendanceContext.expectedDates.contains(a.getDate()))
                    .count();

                double score = total == 0 ? 0.0 : (onTime * 100.0) / total;
                return MetricResult.of(score, Map.of(
                    "onTimeDays", onTime,
                    "checkedInDays", total
                ));
            }

            case "consistency": {
                List<Integer> checkInMinutes = quarterAttendances.stream()
                    .filter(a -> a.getDate() != null && a.getCheckIn() != null)
                    .filter(a -> attendanceContext.expectedDates.contains(a.getDate()))
                    .map(a -> {
                        AttendancePolicyService.AttendancePolicy policy = attendanceContext.policyByDate.get(a.getDate());
                        LocalTime checkInLocal = policy != null && policy.schedule() != null
                            ? resolveCheckInTime(a, policy.schedule())
                            : a.getCheckIn().toLocalTime();
                        return checkInLocal.getHour() * 60 + checkInLocal.getMinute();
                    })
                    .toList();

                if (checkInMinutes.size() < 3) {
                    return MetricResult.of(0.0, Map.of("reason", "insufficient_checkins"));
                }

                double mean = checkInMinutes.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(540.0);

                double variance = checkInMinutes.stream()
                    .mapToDouble(m -> Math.pow(m - mean, 2))
                    .average()
                    .orElse(0);

                double stdDev = Math.sqrt(variance);
                double score;
                if (stdDev < 15) score = Math.min(100, 100 - stdDev);
                else if (stdDev < 30) score = Math.min(90, 90 - (stdDev - 15) / 2);
                else if (stdDev < 60) score = Math.min(75, 75 - (stdDev - 30) / 3);
                else score = Math.max(30, 60 - (stdDev - 60) / 2);

                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("checkIns", checkInMinutes.size());
                raw.put("stdDevMinutes", stdDev);
                return MetricResult.of(score, raw);
            }

            case "reliability":
                return calculateAttendanceMetric(profile, "attendance_rate", config, quarterStart, now, context);

            default:
                return calculateAttendanceMetric(profile, "attendance_punctuality", config, quarterStart, now, context);
        }
    }

    // ============================================================
    // COMPLIANCE METRICS (100% Automated)
    // ============================================================

    private MetricResult calculateComplianceMetric(UUID employeeId, String metricKey,
            DepartmentKpiConfig.MetricConfig config, LocalDate quarterStart, LocalDate now) {

        try {
            long compliant = complianceRepository.countByUserIdAndStatus(employeeId, "compliant");
            long total = complianceRepository.countByUserId(employeeId);
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("compliant", compliant);
            raw.put("total", total);

            if (total == 0) {
                raw.put("reason", "no_requirements");
                return MetricResult.of(100.0, raw);
            }

            switch (metricKey) {
                case "policy_compliance":
                case "compliance":
                case "process_adherence":
                case "audit_compliance":
                    return MetricResult.of((compliant * 100.0) / total, raw);

                case "zero_violations":
                    // If there are non-compliant items, deduct
                    long violations = total - compliant;
                    raw.put("violations", violations);
                    double score = violations == 0 ? 100.0 : Math.max(0, 100 - (violations * 25));
                    return MetricResult.of(score, raw);

                default:
                    return MetricResult.of((compliant * 100.0) / total, raw);
            }
        } catch (Exception e) {
            return MetricResult.of(0.0, Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // TRAINING METRICS (Partially Automated - depends on data)
    // ============================================================

    private MetricResult calculateTrainingMetric(UUID employeeId, String metricKey,
            DepartmentKpiConfig.MetricConfig config, LocalDate quarterStart, LocalDate now) {

        String currentQuarter = getCurrentQuarter();
        int currentYear = now.getYear();

        try {
            // Get training records for this quarter
            long quarterCerts = trainingRepository.countApprovedInQuarter(
                employeeId, currentQuarter, currentYear);
            long totalCerts = trainingRepository.countByEmployeeIdAndStatus(employeeId, "approved");
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("quarterCerts", quarterCerts);
            raw.put("totalCerts", totalCerts);

            switch (metricKey) {
                case "certifications":
                case "hr_certifications":
                case "finance_certifications":
                    // At least 1 cert per quarter = 100%
                    double certTarget = config.target > 0 ? config.target : 1.0;
                    raw.put("targetCerts", certTarget);
                    return MetricResult.of(
                        quarterCerts >= certTarget ? 100.0 : Math.min(100, (quarterCerts / certTarget) * 100),
                        raw
                    );

                case "training_completion":
                case "training":
                case "product_knowledge":
                    // Based on whether they have any training records this quarter
                    // 100% if cert uploaded, 70% if has historical certs, 30% if none
                    return MetricResult.of(
                        quarterCerts > 0 ? 100.0 : (totalCerts > 0 ? 70.0 : 30.0),
                        raw
                    );

                case "training_hours":
                case "skill_development":
                    // HONEST APPROACH:
                    // Option 1: If TrainingRecord has duration_hours field, use it
                    // Option 2: Estimate based on certificates (each cert assumed = 4 hours)
                    // Option 3: If no data, return 50% (neutral)
                    
                    // For now, using estimation:
                    // This is clearly documented as an estimate.
                    // To make this accurate, add duration_hours to TrainingRecord entity.
                    double estimatedHours = quarterCerts * 4.0; // 4 hours per certificate
                    double hourTarget = config.target > 0 ? config.target : 8.0; // Default 8 hours/quarter
                    raw.put("estimatedHours", estimatedHours);
                    raw.put("targetHours", hourTarget);

                    double score = quarterCerts == 0 ? 0.0 : Math.min(100, (estimatedHours / hourTarget) * 100);
                    return MetricResult.of(score, raw);

                case "training_participation":
                    return MetricResult.of(quarterCerts > 0 ? 100.0 : 0.0, raw);

                default:
                    return MetricResult.of(quarterCerts > 0 ? 85.0 : 30.0, raw);
            }
        } catch (Exception e) {
            log.warn("Training metric error: {}", e.getMessage());
            return MetricResult.of(0.0, Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // DAILY REPORT METRICS (100% Automated)
    // Integrates daily report AI scores into Technical Competence
    // ============================================================

    private MetricResult calculateDailyReportMetric(UUID employeeId, String metricKey,
            DepartmentKpiConfig.MetricConfig config, LocalDate quarterStart, LocalDate now, CalculationContext context) {

        if (dailyReportRepository == null) {
            // Daily reports module not available
            return MetricResult.of(0.0, Map.of("reason", "daily_report_module_unavailable"));
        }

        try {
            List<DailyReport> reports = dailyReportRepository.findByEmployeeAndDateRange(
                employeeId, quarterStart, now);

            if (reports.isEmpty()) {
                return MetricResult.of(0.0, Map.of("reports", 0));
            }

            AttendanceContext attendanceContext = getAttendanceContext(context);
            int expectedWorkDays = attendanceContext.expectedWorkDays;
            long reportsOnWorkDays = reports.stream()
                .filter(r -> attendanceContext.expectedDates.contains(r.getReportDate()))
                .count();

            switch (metricKey) {
                case "daily_report_submission":
                case "report_submission_rate":
                    // Score based on submission rate on scheduled work days
                    double submissionRate = expectedWorkDays > 0 ? Math.min(100, (reportsOnWorkDays * 100.0) / expectedWorkDays) : 0.0;
                    return MetricResult.of(submissionRate, Map.of(
                        "expectedWorkDays", expectedWorkDays,
                        "reportsOnWorkDays", reportsOnWorkDays
                    ));

                case "daily_report_quality":
                case "report_ai_score":
                    // Average AI score from daily reports
                    double avgAiScore = reports.stream()
                        .filter(r -> r.getAiScore() != null)
                        .mapToDouble(r -> r.getAiScore().doubleValue())
                        .average()
                        .orElse(0.0);
                    return MetricResult.of(avgAiScore, Map.of(
                        "reportsWithScore", reports.stream().filter(r -> r.getAiScore() != null).count(),
                        "averageAiScore", avgAiScore
                    ));

                case "kpi_alignment":
                case "daily_kpi_alignment":
                    // Average KPI alignment score from daily reports
                    double avgKpiAlignment = reports.stream()
                        .filter(r -> r.getKpiAlignmentScore() != null)
                        .mapToDouble(r -> r.getKpiAlignmentScore().doubleValue())
                        .average()
                        .orElse(0.0);
                    return MetricResult.of(avgKpiAlignment, Map.of(
                        "reportsWithKpiAlignment", reports.stream().filter(r -> r.getKpiAlignmentScore() != null).count(),
                        "averageKpiAlignmentScore", avgKpiAlignment
                    ));

                case "daily_report_score":
                case "daily_productivity":
                default:
                    // Combined score: 40% submission rate + 60% AI score
                    double submissionScore = calculateDailyReportMetric(
                        employeeId, "daily_report_submission", config, quarterStart, now, context).score;
                    double aiQuality = calculateDailyReportMetric(
                        employeeId, "daily_report_quality", config, quarterStart, now, context).score;
                    double score = (submissionScore * 0.4) + (aiQuality * 0.6);
                    return MetricResult.of(score, Map.of(
                        "submissionScore", submissionScore,
                        "aiQualityScore", aiQuality
                    ));
            }
        } catch (Exception e) {
            log.warn("Daily report metric error: {}", e.getMessage());
            return MetricResult.of(0.0, Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // TEAM LEAD RATINGS (Only for subjective metrics)
    // ============================================================

    private MetricResult calculateTeamLeadRating(UUID employeeId, String metricKey,
            DepartmentKpiConfig.MetricConfig config, LocalDate quarterStart) {

        // Get the most recent weekly report
        List<WeeklyPerformanceReport> reports = weeklyReportRepository
            .findByEmployeeIdOrderByYearDescWeekNumberDesc(employeeId);

        if (reports.isEmpty()) {
            return MetricResult.of(0.0, Map.of("reason", "no_weekly_reports"));
        }

        // Get the most recent report
        WeeklyPerformanceReport latest = reports.get(0);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("reportId", latest.getId());
        raw.put("weekNumber", latest.getWeekNumber());
        raw.put("year", latest.getYear());

        switch (metricKey) {
            case "initiative":
            case "self_initiative":
                Integer initiative = latest.getInitiativeScore();
                raw.put("initiativeScore", initiative);
                return MetricResult.of(initiative != null ? (initiative / 5.0) * 100 : 0.0, raw);

            case "attitude":
            case "attitude_towards_work":
                Integer attitude = latest.getAttitudeTowardsWorkScore();
                raw.put("attitudeScore", attitude);
                return MetricResult.of(attitude != null ? (attitude / 5.0) * 100 : 0.0, raw);

            case "professionalism":
            case "communication":
                Integer teamwork = latest.getTeamworkCollaborationScore();
                raw.put("teamworkScore", teamwork);
                return MetricResult.of(teamwork != null ? (teamwork / 5.0) * 100 : 0.0, raw);

            case "adaptability":
                Integer adaptability = latest.getAdaptabilityScore();
                raw.put("adaptabilityScore", adaptability);
                return MetricResult.of(adaptability != null ? (adaptability / 5.0) * 100 : 0.0, raw);

            case "integrity":
            case "confidentiality":
                Integer integrity = latest.getIntegrityScore();
                raw.put("integrityScore", integrity);
                return MetricResult.of(integrity != null ? (integrity / 5.0) * 100 : 0.0, raw);

            case "quality":
            case "attention_to_detail":
            case "accuracy":
                // Use technical score as proxy
                Integer technical = latest.getTechnicalScore();
                raw.put("technicalScore", technical);
                return MetricResult.of(technical != null ? (technical / 5.0) * 100 : 0.0, raw);

            case "reliability":
            case "learning":
            case "skill_application":
            case "trend_awareness":
            case "culture_champion":
            case "brand_alignment":
            case "creativity":
                // Use growth or behavioral as proxy
                Integer growth = latest.getGrowthLearningScore();
                Integer behavioral = latest.getBehavioralScore();
                raw.put("growthScore", growth);
                raw.put("behavioralScore", behavioral);
                if (growth != null) return MetricResult.of((growth / 5.0) * 100, raw);
                if (behavioral != null) return MetricResult.of((behavioral / 5.0) * 100, raw);
                return MetricResult.of(0.0, raw);

            default:
                // Average of available scores
                double sum = 0;
                int count = 0;
                if (latest.getTechnicalScore() != null) { sum += latest.getTechnicalScore(); count++; }
                if (latest.getBehavioralScore() != null) { sum += latest.getBehavioralScore(); count++; }
                if (latest.getCultureFitScore() != null) { sum += latest.getCultureFitScore(); count++; }
                if (latest.getGrowthLearningScore() != null) { sum += latest.getGrowthLearningScore(); count++; }
                raw.put("scoreCount", count);
                raw.put("averageRawScore", count > 0 ? sum / count : 0.0);
                return MetricResult.of(count > 0 ? ((sum / count) / 5.0) * 100 : 0.0, raw);
        }
    }


    // ============================================================
    // HISTORICAL/DERIVED METRICS (Requires stored historical data)
    // ============================================================

    private MetricResult calculateHistoricalMetric(UUID employeeId, String metricKey,
            DepartmentKpiConfig.MetricConfig config) {

        switch (metricKey) {
            case "improvement_trend":
            case "improvement":
            case "performance_trend":
                // REAL IMPLEMENTATION:
                // Compare current quarter's weekly reports average to previous quarter
                return MetricResult.of(calculateImprovementFromWeeklyReports(employeeId), Map.of("metric", "weekly_reports_trend"));

            default:
                return MetricResult.of(0.0, Map.of("reason", "unsupported_historical_metric"));
        }
    }

    /**
     * Calculate improvement trend by comparing recent weekly reports to older ones.
     * Takes average of last 4 weeks vs average of 4 weeks before that.
     */
    private double calculateImprovementFromWeeklyReports(UUID employeeId) {
        try {
            List<WeeklyPerformanceReport> reports = weeklyReportRepository
                .findByEmployeeIdOrderByYearDescWeekNumberDesc(employeeId);

            if (reports.size() < 4) {
                // Not enough data - 0 for new users
                return 0.0;
            }

            // Calculate average of recent reports (last 4)
            double recentAvg = reports.stream()
                .limit(4)
                .mapToDouble(r -> {
                    double sum = 0;
                    int count = 0;
                    if (r.getTechnicalScore() != null) { sum += r.getTechnicalScore(); count++; }
                    if (r.getBehavioralScore() != null) { sum += r.getBehavioralScore(); count++; }
                    if (r.getCultureFitScore() != null) { sum += r.getCultureFitScore(); count++; }
                    if (r.getGrowthLearningScore() != null) { sum += r.getGrowthLearningScore(); count++; }
                    return count > 0 ? sum / count : 3.0; // Default to 3/5
                })
                .average()
                .orElse(3.0);

            // If we have older reports, compare to them
            if (reports.size() >= 8) {
                double olderAvg = reports.stream()
                    .skip(4)
                    .limit(4)
                    .mapToDouble(r -> {
                        double sum = 0;
                        int count = 0;
                        if (r.getTechnicalScore() != null) { sum += r.getTechnicalScore(); count++; }
                        if (r.getBehavioralScore() != null) { sum += r.getBehavioralScore(); count++; }
                        if (r.getCultureFitScore() != null) { sum += r.getCultureFitScore(); count++; }
                        if (r.getGrowthLearningScore() != null) { sum += r.getGrowthLearningScore(); count++; }
                        return count > 0 ? sum / count : 3.0;
                    })
                    .average()
                    .orElse(3.0);

                // Calculate improvement
                // If recentAvg = 4.0, olderAvg = 3.5 → improvement = +0.5
                // Convert to percentage: improvement >= 0.5 = 100%, 0 = 50%, -0.5 = 0%
                double improvement = recentAvg - olderAvg;
                
                // Scale: +1 point = 100%, 0 = 50%, -1 = 0%
                double score = 50 + (improvement * 50);
                return Math.max(0, Math.min(100, score));
            }

            // Only have recent data, base on absolute score
            // 4/5 average = 80%, 3/5 = 60%, etc.
            return (recentAvg / 5.0) * 100;

        } catch (Exception e) {
            log.warn("Error calculating improvement trend: {}", e.getMessage());
            return 50.0; // Neutral on error
        }
    }

    // ============================================================
    // INTERNAL CONTEXT + RESULT TYPES
    // ============================================================

    private static class CalculationContext {
        private final Profile profile;
        private final LocalDate quarterStart;
        private final LocalDate now;
        private final Map<String, Object> cache = new HashMap<>();

        private CalculationContext(Profile profile, LocalDate quarterStart, LocalDate now) {
            this.profile = profile;
            this.quarterStart = quarterStart;
            this.now = now;
        }

        @SuppressWarnings("unchecked")
        private <T> T getOrCompute(String key, Supplier<T> supplier) {
            Object existing = cache.get(key);
            if (existing != null) {
                return (T) existing;
            }
            T value = supplier.get();
            cache.put(key, value);
            return value;
        }
    }

    private static class MetricResult {
        private final double score;
        private final Map<String, Object> rawData;

        private MetricResult(double score, Map<String, Object> rawData) {
            this.score = score;
            this.rawData = rawData != null ? rawData : Map.of();
        }

        private static MetricResult of(double score, Map<String, Object> rawData) {
            return new MetricResult(score, rawData);
        }
    }

    private static class TaskContext {
        private final List<Task> tasks;
        private final double expectedTasks;

        private TaskContext(List<Task> tasks, double expectedTasks) {
            this.tasks = tasks;
            this.expectedTasks = expectedTasks;
        }
    }

    private static class AttendanceContext {
        private final List<Attendance> attendances;
        private final Map<LocalDate, AttendancePolicyService.AttendancePolicy> policyByDate;
        private final Set<LocalDate> expectedDates;
        private final int expectedWorkDays;

        private AttendanceContext(List<Attendance> attendances,
                                  Map<LocalDate, AttendancePolicyService.AttendancePolicy> policyByDate,
                                  Set<LocalDate> expectedDates,
                                  int expectedWorkDays) {
            this.attendances = attendances;
            this.policyByDate = policyByDate;
            this.expectedDates = expectedDates;
            this.expectedWorkDays = expectedWorkDays;
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    public void calculateAndSaveEmployeeScore(Profile profile) {
        Map<String, Object> scoreData = calculateEmployeeScore(profile);
        
        // Save sub-metric scores if repository is available
        if (subMetricScoreRepository != null) {
            saveSubMetricScores(profile.getId(), scoreData);
        }
    }

    private void saveSubMetricScores(UUID employeeId, Map<String, Object> scoreData) {
        if (subMetricScoreRepository == null) return;

        Object pillarsObj = scoreData.get("pillars");
        if (!(pillarsObj instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> pillars = (Map<String, Object>) pillarsObj;
        String quarter = scoreData.get("quarter") != null ? scoreData.get("quarter").toString() : getCurrentQuarter();
        Integer year = scoreData.get("year") instanceof Integer
            ? (Integer) scoreData.get("year")
            : LocalDate.now().getYear();
        int weekNumber = LocalDate.now().get(WeekFields.ISO.weekOfYear());

        for (Map.Entry<String, Object> pillarEntry : pillars.entrySet()) {
            String pillarKey = pillarEntry.getKey();
            if (!(pillarEntry.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pillar = (Map<String, Object>) pillarEntry.getValue();
            Object subMetricsObj = pillar.get("subMetrics");
            if (!(subMetricsObj instanceof List<?>)) {
                continue;
            }

            for (Object subMetricObj : (List<?>) subMetricsObj) {
                if (!(subMetricObj instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> subMetric = (Map<String, Object>) subMetricObj;
                String metricKey = subMetric.get("key") != null ? subMetric.get("key").toString() : null;
                if (metricKey == null) {
                    continue;
                }
                Double score = subMetric.get("score") instanceof Number ? ((Number) subMetric.get("score")).doubleValue() : null;
                String source = subMetric.get("source") != null ? subMetric.get("source").toString() : "auto";

                String rawJson = null;
                Object rawData = subMetric.get("rawData");
                if (rawData != null) {
                    try {
                        rawJson = objectMapper.writeValueAsString(rawData);
                    } catch (Exception e) {
                        rawJson = rawData.toString();
                    }
                }

                SubMetricScore existing = subMetricScoreRepository
                    .findByEmployeeIdAndPillarAndSubMetricAndQuarterAndYear(employeeId, pillarKey, metricKey, quarter, year)
                    .orElse(null);

                SubMetricScore record = existing != null ? existing
                    : new SubMetricScore(employeeId, pillarKey, metricKey, score != null ? score : 0.0, source, quarter, year);
                if (score != null) {
                    record.setScore(score);
                }
                record.setSource(source);
                record.setRawData(rawJson);
                record.setCalculatedAt(OffsetDateTime.now());
                record.setWeekNumber(weekNumber);

                subMetricScoreRepository.save(record);
            }
        }
    }

    private TaskContext getTaskContext(CalculationContext context) {
        return context.getOrCompute("taskContext", () -> {
            OffsetDateTime start = context.quarterStart.atStartOfDay().atOffset(ZoneOffset.UTC);
            List<Task> tasks = taskRepository.findByAssigneeIdAndCreatedAtAfterOrderByCreatedAtDesc(
                context.profile.getId(), start
            );
            double expectedTasks = expectedTasksForProfile(context.profile);
            return new TaskContext(tasks, expectedTasks);
        });
    }

    private AttendanceContext getAttendanceContext(CalculationContext context) {
        return context.getOrCompute("attendanceContext", () -> {
            List<Attendance> attendances = attendanceRepository.findByUserIdOrderByDateDesc(context.profile.getId())
                .stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(context.quarterStart) && !a.getDate().isAfter(context.now))
                .toList();

            Map<LocalDate, AttendancePolicyService.AttendancePolicy> policyByDate = new HashMap<>();
            Set<LocalDate> expectedDates = new HashSet<>();

            for (LocalDate date = context.quarterStart; !date.isAfter(context.now); date = date.plusDays(1)) {
                AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(context.profile.getId(), date);
                policyByDate.put(date, policy);
                if (policy.isWorkDay() && !policy.isHoliday() && !policy.isOnLeave()) {
                    expectedDates.add(date);
                }
            }

            return new AttendanceContext(attendances, policyByDate, expectedDates, expectedDates.size());
        });
    }

    private Map<String, Long> buildStatusBreakdown(List<Task> tasks) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Task.TaskStatus status : Task.TaskStatus.values()) {
            breakdown.put(status.name(), 0L);
        }
        for (Task task : tasks) {
            Task.TaskStatus status = normalizeTaskStatus(task.getStatus());
            breakdown.put(status.name(), breakdown.getOrDefault(status.name(), 0L) + 1);
        }
        return breakdown;
    }

    private Task.TaskStatus normalizeTaskStatus(String status) {
        if (status == null) {
            return Task.TaskStatus.TODO;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DONE", "COMPLETED" -> Task.TaskStatus.DONE;
            case "IN_PROGRESS", "IN PROGRESS" -> Task.TaskStatus.IN_PROGRESS;
            case "REVIEW" -> Task.TaskStatus.REVIEW;
            case "CANCELLED", "CANCELED" -> Task.TaskStatus.CANCELLED;
            case "PENDING", "TODO" -> Task.TaskStatus.TODO;
            case "OVERDUE" -> Task.TaskStatus.IN_PROGRESS;
            default -> Task.TaskStatus.TODO;
        };
    }

    private boolean isTaskDone(Task task) {
        return normalizeTaskStatus(task.getStatus()) == Task.TaskStatus.DONE;
    }

    private LocalTime resolveCheckInTime(Attendance attendance, WorkSchedule schedule) {
        ZoneId zone = attendancePolicyService.resolveZone(schedule, attendance.getOfficeLocationId());
        return attendance.getCheckIn().atZoneSameInstant(zone).toLocalTime();
    }

    private double expectedTasksForProfile(Profile profile) {
        int level = profile.getJobLevel() != null ? profile.getJobLevel() : 1;
        double baseline = 8 + (Math.max(0, level - 1) * 2);
        if (Boolean.TRUE.equals(profile.getIsTeamLead())) {
            baseline += 4;
        }
        return baseline;
    }

    private LocalDate getQuarterStart() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
        return LocalDate.of(now.getYear(), quarterStartMonth, 1);
    }

    private String getCurrentQuarter() {
        return getQuarterForDate(LocalDate.now());
    }

    private String getQuarterForDate(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    private String calculateGrade(double score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private double calculateQgpa(double score) {
        return Math.round((score / 25.0) * 100.0) / 100.0;
    }

    private String formatPillarName(String key) {
        switch (key) {
            case "technical": return "Technical Competence";
            case "behavioral": return "Behavioral Competency";
            case "culture_fit": return "Culture Fit";
            case "growth": return "Growth & Learning";
            default: return key.substring(0, 1).toUpperCase() + key.substring(1).replace("_", " ");
        }
    }

    private int getQuarterStartWeek(LocalDate quarterStart) {
        return quarterStart.get(java.time.temporal.WeekFields.ISO.weekOfYear());
    }
}
