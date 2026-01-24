package com.schoolable.backend.performance;

import com.schoolable.backend.attendance.AttendancePolicyService;
import com.schoolable.backend.attendance.WorkSchedule;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Daily Reports
 * Staff submit daily reports which are AI-graded and contribute to Technical Competence pillar.
 */
@RestController
@RequestMapping("/api/daily-reports")
@Tag(name = "Daily Reports")
public class DailyReportController {

    private static final LocalTime REPORT_CUTOFF = LocalTime.of(18, 0);

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private DailyReportAiService dailyReportAiService;

    @Autowired
    private AttendancePolicyService attendancePolicyService;

    // ==================== MY REPORTS ====================

    @Operation(summary = "Get my daily reports")
    @GetMapping("/my")
    public ResponseEntity<?> getMyReports(
            Authentication auth,
            @RequestParam(required = false) Integer limit
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        List<DailyReport> reports;
        if (limit != null && limit > 0) {
            reports = dailyReportRepository.findByEmployeeIdOrderByReportDateDesc(
                userId, org.springframework.data.domain.PageRequest.of(0, limit)
            );
        } else {
            reports = dailyReportRepository.findByEmployeeIdOrderByReportDateDesc(userId);
        }

        return ResponseEntity.ok(reports.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @Operation(summary = "Get today's report status")
    @GetMapping("/today")
    public ResponseEntity<?> getTodayReport(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        ZoneId zone = resolveUserZone(userId, LocalDate.now());
        LocalDate today = LocalDate.now(zone);

        Optional<DailyReport> report = dailyReportRepository.findByEmployeeIdAndReportDate(userId, today);
        SubmissionWindow window = buildSubmissionWindow(today, zone);

        Map<String, Object> response = new HashMap<>();
        response.put("hasSubmittedToday", report.isPresent());
        response.put("date", today.toString());
        response.put("submissionWindow", toSubmissionWindowPayload(window));
        
        if (report.isPresent()) {
            response.put("report", toDto(report.get()));
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Submit a daily report")
    @PostMapping
    public ResponseEntity<?> submitReport(Authentication auth, @RequestBody SubmitReportRequest request) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        ZoneId zone = resolveUserZone(userId, LocalDate.now());
        LocalDate today = LocalDate.now(zone);
        LocalDate reportDate = request.reportDate() != null ? request.reportDate() : today;
        SubmissionWindow window = buildSubmissionWindow(reportDate, zone);

        if (!reportDate.equals(today)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Reports can only be submitted for today",
                "serverDate", today.toString(),
                "timezone", zone.getId(),
                "submissionWindow", toSubmissionWindowPayload(window)
            ));
        }

        if (!window.canSubmit()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Submission window closed at " + REPORT_CUTOFF,
                "serverDate", today.toString(),
                "timezone", zone.getId(),
                "submissionWindow", toSubmissionWindowPayload(window)
            ));
        }

        // Check if already submitted for this date
        Optional<DailyReport> existing = dailyReportRepository.findByEmployeeIdAndReportDate(userId, reportDate);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Report already submitted for " + reportDate,
                "existingReportId", existing.get().getId()
            ));
        }

        // Validate content
        if (request.tasksCompleted() == null || request.tasksCompleted().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tasks completed is required"));
        }

        // Create report
        DailyReport report = new DailyReport();
        report.setEmployeeId(userId);
        report.setReportDate(reportDate);
        report.setTasksCompleted(request.tasksCompleted());
        report.setTasksInProgress(request.tasksInProgress());
        report.setBlockers(request.blockers());
        report.setPlannedForTomorrow(request.plannedForTomorrow());
        report.setAdditionalNotes(request.additionalNotes());
        report.setAttachmentUrl(request.attachmentUrl());
        report.setAttachmentName(request.attachmentName());

        report = dailyReportRepository.save(report);

        dailyReportAiService.enqueueAiGrading(report.getId(), userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Daily report submitted successfully",
            "report", toDto(report)
        ));
    }

    @Operation(summary = "Update a daily report (same day only)")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateReport(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody SubmitReportRequest request
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<DailyReport> reportOpt = dailyReportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
        }

        DailyReport report = reportOpt.get();
        if (!report.getEmployeeId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Can only edit your own reports"));
        }

        // Only allow edits on same day
        ZoneId zone = resolveUserZone(userId, LocalDate.now());
        LocalDate today = LocalDate.now(zone);
        if (!report.getReportDate().equals(today)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Can only edit today's report"));
        }

        // Update fields
        if (request.tasksCompleted() != null) report.setTasksCompleted(request.tasksCompleted());
        if (request.tasksInProgress() != null) report.setTasksInProgress(request.tasksInProgress());
        if (request.blockers() != null) report.setBlockers(request.blockers());
        if (request.plannedForTomorrow() != null) report.setPlannedForTomorrow(request.plannedForTomorrow());
        if (request.additionalNotes() != null) report.setAdditionalNotes(request.additionalNotes());
        if (request.attachmentUrl() != null) report.setAttachmentUrl(request.attachmentUrl());
        if (request.attachmentName() != null) report.setAttachmentName(request.attachmentName());

        report = dailyReportRepository.save(report);

        dailyReportAiService.enqueueAiGrading(report.getId(), userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Report updated successfully",
            "report", toDto(report)
        ));
    }

    @Operation(summary = "Regrade a daily report (Admin)")
    @PostMapping("/{id}/regrade")
    public ResponseEntity<?> regradeReport(Authentication auth, @PathVariable Long id) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Optional<DailyReport> reportOpt = dailyReportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
        }

        DailyReport report = reportOpt.get();
        var job = dailyReportAiService.enqueueAiGrading(report.getId(), report.getEmployeeId());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "AI grading queued",
            "reportId", report.getId(),
            "jobId", job.getId()
        ));
    }

    // ==================== STATS ====================

    @Operation(summary = "Get my report stats")
    @GetMapping("/stats")
    public ResponseEntity<?> getMyStats(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Get this week's reports
        ZoneId zone = resolveUserZone(userId, LocalDate.now());
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        Long weeklyCount = dailyReportRepository.countByEmployeeAndWeek(userId, weekStart, weekEnd);
        Double weeklyAvgScore = dailyReportRepository.getAverageAiScore(userId, weekStart, weekEnd);

        // Get this month's average
        LocalDate monthStart = today.withDayOfMonth(1);
        Double monthlyAvgScore = dailyReportRepository.getAverageAiScore(userId, monthStart, today);

        // Get this quarter's average
        int quarter = (today.getMonthValue() - 1) / 3;
        LocalDate quarterStart = LocalDate.of(today.getYear(), quarter * 3 + 1, 1);
        Double quarterlyAvgScore = dailyReportRepository.getAverageAiScore(userId, quarterStart, today);

        return ResponseEntity.ok(Map.of(
            "weeklyReportsSubmitted", weeklyCount,
            "weeklyTargetDays", 5, // Mon-Fri
            "weeklyAverageScore", weeklyAvgScore != null ? weeklyAvgScore : 0,
            "monthlyAverageScore", monthlyAvgScore != null ? monthlyAvgScore : 0,
            "quarterlyAverageScore", quarterlyAvgScore != null ? quarterlyAvgScore : 0,
            "hasSubmittedToday", dailyReportRepository.existsByEmployeeIdAndReportDate(userId, today),
            "serverDate", today.toString(),
            "timezone", zone.getId()
        ));
    }

    @Operation(summary = "Get org-wide daily report stats (Admin)")
    @GetMapping("/stats/org-wide")
    public ResponseEntity<?> getOrgWideStats(
            Authentication auth,
            @RequestParam(required = false) String date) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();

        long submittedToday = Optional.ofNullable(dailyReportRepository.countByReportDate(targetDate)).orElse(0L);
        long submittedYesterday = Optional.ofNullable(dailyReportRepository.countByReportDate(targetDate.minusDays(1))).orElse(0L);

        long totalStaff = profileRepository.findAll().stream()
            .filter(p -> p.getRole() == null || !p.getRole().toLowerCase().contains("admin"))
            .count();

        int trendChange = 0;
        if (submittedYesterday > 0) {
            trendChange = (int) Math.round(((submittedToday - submittedYesterday) * 100.0) / submittedYesterday);
        }

        return ResponseEntity.ok(Map.of(
            "date", targetDate.toString(),
            "submittedToday", submittedToday,
            "totalStaff", totalStaff,
            "trendChange", trendChange
        ));
    }

    @Operation(summary = "Get org-wide daily report stats range (Admin)")
    @GetMapping("/stats/range")
    public ResponseEntity<?> getOrgWideStatsRange(
            Authentication auth,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        long totalStaff = profileRepository.findAll().stream()
            .filter(p -> p.getRole() == null || !p.getRole().toLowerCase().contains("admin"))
            .count();

        Map<LocalDate, Long> countsByDate = new HashMap<>();
        for (Object[] row : dailyReportRepository.countByReportDateRange(start, end)) {
            LocalDate date = (LocalDate) row[0];
            Number count = (Number) row[1];
            countsByDate.put(date, count != null ? count.longValue() : 0L);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            long submitted = countsByDate.getOrDefault(cursor, 0L);
            int submissionRate = totalStaff > 0 ? (int) Math.round((submitted * 100.0) / totalStaff) : 0;
            days.add(Map.of(
                "date", cursor.toString(),
                "submitted", submitted,
                "submissionRate", submissionRate
            ));
        }

        return ResponseEntity.ok(Map.of(
            "startDate", startDate,
            "endDate", endDate,
            "totalStaff", totalStaff,
            "days", days
        ));
    }

    @Operation(summary = "Get daily report attachments (Admin)")
    @GetMapping("/attachments")
    public ResponseEntity<?> getReportAttachments(
            Authentication auth,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.of(2000, 1, 1);

        List<DailyReport> reports = dailyReportRepository.findWithAttachmentsInRange(start, end);
        Set<UUID> employeeIds = reports.stream()
            .map(DailyReport::getEmployeeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<UUID, Profile> profileMap = profileRepository.findAllById(employeeIds).stream()
            .collect(Collectors.toMap(Profile::getId, p -> p));

        List<Map<String, Object>> attachments = new ArrayList<>();
        for (DailyReport report : reports) {
            if (report.getAttachmentUrl() == null || report.getAttachmentUrl().isBlank()) {
                continue;
            }
            Profile employee = profileMap.get(report.getEmployeeId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", report.getId());
            item.put("employeeId", report.getEmployeeId());
            item.put("employeeName", employee != null ? employee.getFullName() : null);
            item.put("department", employee != null ? employee.getDepartment() : null);
            item.put("reportDate", report.getReportDate() != null ? report.getReportDate().toString() : null);
            item.put("attachmentUrl", report.getAttachmentUrl());
            item.put("attachmentName", report.getAttachmentName());
            item.put("status", report.getStatus());
            item.put("createdAt", report.getCreatedAt());
            attachments.add(item);
        }

        return ResponseEntity.ok(Map.of(
            "startDate", start.toString(),
            "endDate", end.toString(),
            "count", attachments.size(),
            "attachments", attachments
        ));
    }

    // ==================== TEAM LEAD VIEWS ====================

    @Operation(summary = "Get team's daily reports (Team Lead)")
    @GetMapping("/team")
    public ResponseEntity<?> getTeamReports(
            Authentication auth,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer days
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Verify user is a team lead
        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only team leads can view team reports"));
        }

        String department = profileOpt.get().getDepartment();
        if (department == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No department assigned"));
        }

        ZoneId zone = resolveUserZone(userId, LocalDate.now());
        LocalDate endDate = date != null ? LocalDate.parse(date) : LocalDate.now(zone);
        LocalDate startDate = days != null ? endDate.minusDays(days) : endDate;

        List<DailyReport> reports = dailyReportRepository.findByDepartmentAndDateRange(department, startDate, endDate);

        // Group by employee
        Map<UUID, List<Map<String, Object>>> byEmployee = new LinkedHashMap<>();
        for (DailyReport r : reports) {
            byEmployee.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>()).add(toDto(r));
        }

        // Add employee info
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<UUID, List<Map<String, Object>>> entry : byEmployee.entrySet()) {
            Profile emp = profileRepository.findById(entry.getKey()).orElse(null);
            Map<String, Object> item = new HashMap<>();
            item.put("employeeId", entry.getKey());
            item.put("employeeName", emp != null ? emp.getFullName() : "Unknown");
            item.put("reports", entry.getValue());
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Review a daily report (Team Lead)")
    @PostMapping("/{id}/review")
    public ResponseEntity<?> reviewReport(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody ReviewReportRequest request
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Verify team lead
        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only team leads can review reports"));
        }

        Optional<DailyReport> reportOpt = dailyReportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
        }

        DailyReport report = reportOpt.get();
        report.setReviewedBy(userId);
        report.setReviewedAt(OffsetDateTime.now());
        report.setReviewerNotes(request.notes());
        if (request.score() != null) {
            report.setReviewerScore(BigDecimal.valueOf(request.score()));
        }
        report.setStatus("reviewed");

        report = dailyReportRepository.save(report);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Report reviewed",
            "report", toDto(report)
        ));
    }

    @Operation(summary = "Get team daily report stats (Team Lead)")
    @GetMapping("/team/stats")
    public ResponseEntity<?> getTeamStats(
            Authentication auth,
            @RequestParam(required = false) String date
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Verify team lead
        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only team leads can view team stats"));
        }

        String department = profileOpt.get().getDepartment();
        if (department == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No department assigned"));
        }

        ZoneId zone = resolveUserZone(userId, LocalDate.now());
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now(zone);

        // Get all team members
        List<Profile> teamMembers = profileRepository.findByDepartmentAndStatus(department, "active");

        // Get reports for target date
        List<DailyReport> reports = dailyReportRepository.findByDepartmentAndDate(department, targetDate);
        Map<UUID, DailyReport> reportsByEmployee = reports.stream()
            .collect(Collectors.toMap(DailyReport::getEmployeeId, r -> r));

        // Calculate stats
        int totalMembers = teamMembers.size();
        int submittedCount = reportsByEmployee.size();
        int pendingCount = totalMembers - submittedCount;

        Double avgScore = reports.stream()
            .filter(r -> r.getAiScore() != null)
            .mapToDouble(r -> r.getAiScore().doubleValue())
            .average()
            .orElse(0);

        // Build member list
        List<Map<String, Object>> membersList = new ArrayList<>();
        for (Profile member : teamMembers) {
            if (member.getId().equals(userId)) continue; // Skip team lead themselves

            Map<String, Object> memberData = new HashMap<>();
            memberData.put("employeeId", member.getId());
            memberData.put("employeeName", member.getFullName());

            DailyReport report = reportsByEmployee.get(member.getId());
            memberData.put("hasSubmitted", report != null);
            memberData.put("aiScore", report != null ? report.getAiScore() : null);
            memberData.put("status", report != null ? report.getStatus() : null);

            membersList.add(memberData);
        }

        return ResponseEntity.ok(Map.of(
            "date", targetDate.toString(),
            "totalMembers", totalMembers - 1, // Exclude team lead
            "submittedCount", submittedCount,
            "pendingCount", pendingCount,
            "averageAiScore", avgScore,
            "members", membersList,
            "timezone", zone.getId()
        ));
    }

    // ==================== HELPER METHODS ====================

    private ZoneId resolveUserZone(UUID userId, LocalDate referenceDate) {
        AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(userId, referenceDate);
        WorkSchedule schedule = policy != null ? policy.schedule() : null;
        return attendancePolicyService.resolveZone(schedule, null);
    }

    private SubmissionWindow buildSubmissionWindow(LocalDate reportDate, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        ZonedDateTime cutoffAt = ZonedDateTime.of(reportDate, REPORT_CUTOFF, zone);
        boolean isToday = reportDate.equals(today);
        boolean isLate = isToday && now.isAfter(cutoffAt);
        boolean canSubmit = isToday && !now.isAfter(cutoffAt);
        long minutesRemaining = isToday
            ? Math.max(0, Duration.between(now, cutoffAt).toMinutes())
            : 0;
        return new SubmissionWindow(zone, today, reportDate, now, cutoffAt, isLate, canSubmit, minutesRemaining);
    }

    private Map<String, Object> toSubmissionWindowPayload(SubmissionWindow window) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverTime", window.now().toString());
        payload.put("timezone", window.zone().getId());
        payload.put("serverDate", window.today().toString());
        payload.put("reportDate", window.reportDate().toString());
        payload.put("cutoffTime", REPORT_CUTOFF.toString());
        payload.put("cutoffAt", window.cutoffAt().toString());
        payload.put("isLate", window.isLate());
        payload.put("canSubmit", window.canSubmit());
        payload.put("minutesRemaining", window.minutesRemaining());
        return payload;
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return false;
        }
        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null || profile.getRole() == null) {
            return false;
        }
        return profile.getRole().toLowerCase().contains("admin");
    }

    private record SubmissionWindow(
        ZoneId zone,
        LocalDate today,
        LocalDate reportDate,
        ZonedDateTime now,
        ZonedDateTime cutoffAt,
        boolean isLate,
        boolean canSubmit,
        long minutesRemaining
    ) {}

    private Map<String, Object> toDto(DailyReport r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", r.getId());
        dto.put("employeeId", r.getEmployeeId());
        dto.put("reportDate", r.getReportDate().toString());
        dto.put("tasksCompleted", r.getTasksCompleted());
        dto.put("tasksInProgress", r.getTasksInProgress());
        dto.put("blockers", r.getBlockers());
        dto.put("plannedForTomorrow", r.getPlannedForTomorrow());
        dto.put("additionalNotes", r.getAdditionalNotes());
        dto.put("attachmentUrl", r.getAttachmentUrl());
        dto.put("attachmentName", r.getAttachmentName());
        dto.put("aiScore", r.getAiScore());
        dto.put("aiFeedback", r.getAiFeedback());
        dto.put("aiSuggestions", r.getAiSuggestions());
        dto.put("aiStrengths", r.getAiStrengths());
        dto.put("aiImprovements", r.getAiImprovements());
        dto.put("aiAuraBoostTips", r.getAiAuraBoostTips());
        dto.put("aiGradedAt", r.getAiGradedAt());
        dto.put("kpiAlignmentScore", r.getKpiAlignmentScore());
        dto.put("aiJobId", r.getAiJobId());
        dto.put("aiRequestId", r.getAiRequestId());
        dto.put("aiPromptVersion", r.getAiPromptVersion());
        dto.put("aiModelUsed", r.getAiModelUsed());
        dto.put("aiStatus", r.getAiStatus());
        dto.put("status", r.getStatus());
        dto.put("reviewedBy", r.getReviewedBy());
        dto.put("reviewedAt", r.getReviewedAt());
        dto.put("reviewerNotes", r.getReviewerNotes());
        dto.put("reviewerScore", r.getReviewerScore());
        dto.put("finalScore", r.getFinalScore());
        dto.put("createdAt", r.getCreatedAt());
        return dto;
    }

    // Request DTOs
    public record SubmitReportRequest(
        LocalDate reportDate,
        String tasksCompleted,
        String tasksInProgress,
        String blockers,
        String plannedForTomorrow,
        String additionalNotes,
        String attachmentUrl,
        String attachmentName
    ) {}

    public record ReviewReportRequest(
        String notes,
        Double score
    ) {}
}
