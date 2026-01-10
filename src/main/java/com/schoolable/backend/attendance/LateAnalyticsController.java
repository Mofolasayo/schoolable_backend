package com.schoolable.backend.attendance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Late Analytics Controller
 * Provides detailed late check-in analytics for the admin dashboard.
 */
@RestController
@RequestMapping("/api/admin/late-analytics")
@Tag(name = "Admin - Late Analytics")
public class LateAnalyticsController {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AttendancePolicyService attendancePolicyService;

    // ==================== MAIN ANALYTICS ====================

    @Operation(summary = "Get late analytics dashboard data")
    @GetMapping
    public ResponseEntity<?> getLateAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdminProfile(admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        // Default: last 30 days
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusDays(30);

        List<Attendance> allAttendance = attendanceRepository.findByDateRange(start, end);
        
        // Filter late check-ins
        List<Map<String, Object>> lateCheckIns = new ArrayList<>();
        Map<UUID, Integer> userLateCounts = new HashMap<>();
        Map<UUID, List<Integer>> userLateMinutes = new HashMap<>();
        Map<String, Integer> reasonCategories = new HashMap<>();
        int totalLate = 0;
        int totalMinutesLate = 0;

        for (Attendance a : allAttendance) {
            int minutesLate = calculateMinutesLate(a);
            
            if (minutesLate > 0) {
                totalLate++;
                totalMinutesLate += minutesLate;

                Profile user = profileRepository.findById(a.getUserId()).orElse(null);
                
                Map<String, Object> lateRecord = new HashMap<>();
                lateRecord.put("id", a.getId());
                lateRecord.put("userId", a.getUserId());
                lateRecord.put("userName", user != null ? user.getFullName() : "Unknown");
                lateRecord.put("userAvatar", user != null ? getAvatarUrl(user) : null);
                lateRecord.put("department", user != null ? user.getDepartment() : null);
                lateRecord.put("checkInTime", formatCheckInTime(a));
                lateRecord.put("minutesLate", minutesLate);
                lateRecord.put("reason", a.getNote() != null ? a.getNote() : "No reason provided");
                lateRecord.put("reasonCategory", categorizeReason(a.getNote()));
                lateRecord.put("date", a.getDate().toString());
                
                lateCheckIns.add(lateRecord);

                // Track per-user stats
                userLateCounts.merge(a.getUserId(), 1, Integer::sum);
                userLateMinutes.computeIfAbsent(a.getUserId(), k -> new ArrayList<>()).add(minutesLate);

                // Track reason categories
                String category = categorizeReason(a.getNote());
                reasonCategories.merge(category, 1, Integer::sum);
            }
        }

        // Calculate on-time rate
        double onTimeRate = allAttendance.isEmpty() ? 100.0 : 
            ((allAttendance.size() - totalLate) * 100.0) / allAttendance.size();

        // Get repeat offenders (3+ late in period)
        List<Map<String, Object>> repeatOffenders = getRepeatOffenders(userLateCounts, userLateMinutes);

        // Daily breakdown for chart
        List<Map<String, Object>> dailyBreakdown = getDailyBreakdown(allAttendance, start, end);

        // Department breakdown
        Map<String, Integer> deptBreakdown = getDepartmentBreakdown(lateCheckIns);

        return ResponseEntity.ok(Map.of(
            "summary", Map.of(
                "totalLateCheckIns", totalLate,
                "averageMinutesLate", totalLate > 0 ? totalMinutesLate / totalLate : 0,
                "onTimeRate", Math.round(onTimeRate * 10) / 10.0,
                "repeatOffenderCount", repeatOffenders.size(),
                "totalAttendanceRecords", allAttendance.size()
            ),
            "lateCheckIns", lateCheckIns,
            "repeatOffenders", repeatOffenders,
            "reasonBreakdown", reasonCategories,
            "dailyBreakdown", dailyBreakdown,
            "departmentBreakdown", deptBreakdown,
            "dateRange", Map.of("start", start.toString(), "end", end.toString())
        ));
    }

    // ==================== REPEAT OFFENDERS ====================

    @Operation(summary = "Get repeat offenders")
    @GetMapping("/repeat-offenders")
    public ResponseEntity<?> getRepeatOffenders(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "3") int minLateCount,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdminProfile(admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        
        List<Attendance> allAttendance = attendanceRepository.findByDateRange(start, end);
        
        // Track late counts
        Map<UUID, Integer> userLateCounts = new HashMap<>();
        Map<UUID, List<Integer>> userLateMinutes = new HashMap<>();
        Map<UUID, LocalDate> userLastLate = new HashMap<>();

        for (Attendance a : allAttendance) {
            int minutesLate = calculateMinutesLate(a);
            if (minutesLate > 0) {
                userLateCounts.merge(a.getUserId(), 1, Integer::sum);
                userLateMinutes.computeIfAbsent(a.getUserId(), k -> new ArrayList<>()).add(minutesLate);
                
                LocalDate existing = userLastLate.get(a.getUserId());
                if (existing == null || a.getDate().isAfter(existing)) {
                    userLastLate.put(a.getUserId(), a.getDate());
                }
            }
        }

        // Filter to those with >= minLateCount
        List<Map<String, Object>> offenders = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : userLateCounts.entrySet()) {
            if (entry.getValue() >= minLateCount) {
                Profile user = profileRepository.findById(entry.getKey()).orElse(null);
                List<Integer> minutes = userLateMinutes.get(entry.getKey());
                int avgMinutes = minutes.stream().mapToInt(i -> i).sum() / minutes.size();

                Map<String, Object> offender = new HashMap<>();
                offender.put("id", entry.getKey());
                offender.put("name", user != null ? user.getFullName() : "Unknown");
                offender.put("avatar", user != null ? getAvatarUrl(user) : null);
                offender.put("department", user != null ? user.getDepartment() : null);
                offender.put("lateCount", entry.getValue());
                offender.put("averageMinutesLate", avgMinutes);
                offender.put("lastLateDate", userLastLate.get(entry.getKey()).toString());
                offender.put("trend", determineTrend(entry.getKey(), start, end));

                offenders.add(offender);
            }
        }

        // Sort by late count descending
        offenders.sort((a, b) -> ((Integer) b.get("lateCount")).compareTo((Integer) a.get("lateCount")));

        return ResponseEntity.ok(Map.of(
            "offenders", offenders,
            "total", offenders.size(),
            "period", Map.of("days", days, "start", start.toString(), "end", end.toString())
        ));
    }

    // ==================== TODAY'S LATE CHECK-INS ====================

    @Operation(summary = "Get today's late check-ins")
    @GetMapping("/today")
    public ResponseEntity<?> getTodayLateCheckIns(Authentication auth) {
        Profile admin = getAdminProfile(auth);
        if (!isAdminProfile(admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        LocalDate today = LocalDate.now();
        List<Attendance> todayAttendance = attendanceRepository.findByDateOrderByCheckInDesc(today);
        
        List<Map<String, Object>> lateCheckIns = new ArrayList<>();
        int onTime = 0;
        int late = 0;

        for (Attendance a : todayAttendance) {
            int minutesLate = calculateMinutesLate(a);
            
            if (minutesLate > 0) {
                late++;
                Profile user = profileRepository.findById(a.getUserId()).orElse(null);
                
                Map<String, Object> record = new HashMap<>();
                record.put("id", a.getId());
                record.put("userId", a.getUserId());
                record.put("userName", user != null ? user.getFullName() : "Unknown");
                record.put("userAvatar", user != null ? getAvatarUrl(user) : null);
                record.put("department", user != null ? user.getDepartment() : null);
                record.put("checkInTime", formatCheckInTime(a));
                record.put("minutesLate", minutesLate);
                record.put("reason", a.getNote());
                
                lateCheckIns.add(record);
            } else {
                onTime++;
            }
        }

        double onTimeRate = todayAttendance.isEmpty() ? 100.0 :
            (onTime * 100.0) / todayAttendance.size();

        return ResponseEntity.ok(Map.of(
            "date", today.toString(),
            "lateCheckIns", lateCheckIns,
            "lateCount", late,
            "onTimeCount", onTime,
            "totalCheckedIn", todayAttendance.size(),
            "onTimeRate", Math.round(onTimeRate * 10) / 10.0
        ));
    }

    // ==================== HELPER METHODS ====================

    private int calculateMinutesLate(Attendance attendance) {
        if (attendance.getCheckIn() == null) return 0;

        AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(
            attendance.getUserId(), attendance.getDate()
        );
        if (!policy.isWorkDay() || policy.isHoliday() || policy.isOnLeave()) return 0;

        WorkSchedule schedule = policy.schedule();
        if (schedule == null || schedule.getStartTime() == null) return 0;

        LocalTime expected = schedule.getStartTime().plusMinutes(
            schedule.getGraceMinutes() != null ? schedule.getGraceMinutes() : 0
        );
        LocalTime checkInTime = resolveCheckInTime(attendance, schedule);
        if (!checkInTime.isAfter(expected)) return 0;

        return (int) Duration.between(expected, checkInTime).toMinutes();
    }

    private String formatCheckInTime(Attendance attendance) {
        if (attendance.getCheckIn() == null) return null;
        AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(
            attendance.getUserId(), attendance.getDate()
        );
        WorkSchedule schedule = policy != null ? policy.schedule() : null;
        return resolveCheckInTime(attendance, schedule).toString();
    }

    private LocalTime resolveCheckInTime(Attendance attendance, WorkSchedule schedule) {
        ZoneId zone = attendancePolicyService.resolveZone(schedule, attendance.getOfficeLocationId());
        return attendance.getCheckIn().atZoneSameInstant(zone).toLocalTime();
    }

    private String categorizeReason(String note) {
        if (note == null || note.isEmpty()) return "other";
        
        String lower = note.toLowerCase();
        if (lower.contains("traffic") || lower.contains("accident") || lower.contains("road")) {
            return "traffic";
        }
        if (lower.contains("health") || lower.contains("sick") || lower.contains("doctor") || lower.contains("medical")) {
            return "health";
        }
        if (lower.contains("weather") || lower.contains("rain") || lower.contains("flood")) {
            return "weather";
        }
        if (lower.contains("family") || lower.contains("child") || lower.contains("emergency")) {
            return "family";
        }
        if (lower.contains("work") || lower.contains("meeting") || lower.contains("client")) {
            return "work";
        }
        return "other";
    }

    private List<Map<String, Object>> getRepeatOffenders(
            Map<UUID, Integer> userLateCounts, 
            Map<UUID, List<Integer>> userLateMinutes
    ) {
        List<Map<String, Object>> offenders = new ArrayList<>();
        
        for (Map.Entry<UUID, Integer> entry : userLateCounts.entrySet()) {
            if (entry.getValue() >= 3) {
                Profile user = profileRepository.findById(entry.getKey()).orElse(null);
                List<Integer> minutes = userLateMinutes.get(entry.getKey());
                int avgMinutes = minutes.stream().mapToInt(i -> i).sum() / minutes.size();

                Map<String, Object> offender = new HashMap<>();
                offender.put("id", entry.getKey());
                offender.put("name", user != null ? user.getFullName() : "Unknown");
                offender.put("avatar", user != null ? getAvatarUrl(user) : null);
                offender.put("department", user != null ? user.getDepartment() : null);
                offender.put("lateCount", entry.getValue());
                offender.put("averageMinutesLate", avgMinutes);
                offender.put("trend", "stable");

                offenders.add(offender);
            }
        }

        offenders.sort((a, b) -> ((Integer) b.get("lateCount")).compareTo((Integer) a.get("lateCount")));
        return offenders;
    }

    private String determineTrend(UUID userId, LocalDate start, LocalDate end) {
        // Compare first half vs second half of period
        LocalDate midpoint = start.plusDays(Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() / 2);
        
        List<Attendance> firstHalf = attendanceRepository.findByDateRange(start, midpoint);
        List<Attendance> secondHalf = attendanceRepository.findByDateRange(midpoint.plusDays(1), end);
        
        int firstHalfLate = 0;
        int secondHalfLate = 0;
        
        for (Attendance a : firstHalf) {
            if (a.getUserId().equals(userId) && calculateMinutesLate(a) > 0) {
                firstHalfLate++;
            }
        }
        for (Attendance a : secondHalf) {
            if (a.getUserId().equals(userId) && calculateMinutesLate(a) > 0) {
                secondHalfLate++;
            }
        }
        
        if (secondHalfLate < firstHalfLate) return "improving";
        if (secondHalfLate > firstHalfLate) return "worsening";
        return "stable";
    }

    private List<Map<String, Object>> getDailyBreakdown(
            List<Attendance> allAttendance, 
            LocalDate start, 
            LocalDate end
    ) {
        Map<LocalDate, int[]> dailyStats = new TreeMap<>();
        
        // Initialize all dates
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dailyStats.put(date, new int[]{0, 0}); // [onTime, late]
        }
        
        for (Attendance a : allAttendance) {
            int[] stats = dailyStats.get(a.getDate());
            if (stats != null) {
                if (calculateMinutesLate(a) > 0) {
                    stats[1]++;
                } else {
                    stats[0]++;
                }
            }
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, int[]> entry : dailyStats.entrySet()) {
            result.add(Map.of(
                "date", entry.getKey().toString(),
                "dayOfWeek", entry.getKey().getDayOfWeek().toString(),
                "onTime", entry.getValue()[0],
                "late", entry.getValue()[1],
                "total", entry.getValue()[0] + entry.getValue()[1]
            ));
        }
        
        return result;
    }

    private Map<String, Integer> getDepartmentBreakdown(List<Map<String, Object>> lateCheckIns) {
        Map<String, Integer> breakdown = new HashMap<>();
        
        for (Map<String, Object> record : lateCheckIns) {
            String dept = (String) record.get("department");
            if (dept != null) {
                breakdown.merge(dept, 1, Integer::sum);
            }
        }
        
        return breakdown;
    }

    private String getAvatarUrl(Profile p) {
        if (p.getAvatarUrl() != null) return p.getAvatarUrl();
        
        String seed = p.getEmployeeId() != null ? p.getEmployeeId() : 
            (p.getEmail() != null ? p.getEmail() : "user");
        String style = "bottts";
        if ("male".equalsIgnoreCase(p.getGender())) style = "adventurer";
        else if ("female".equalsIgnoreCase(p.getGender())) style = "adventurer-neutral";
        
        return "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
    }

    private Profile getAdminProfile(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        return profileRepository.findById((UUID) auth.getPrincipal()).orElse(null);
    }

    private boolean isAdminProfile(Profile profile) {
        if (profile == null || profile.getRole() == null) return false;
        String role = profile.getRole().toLowerCase(Locale.ROOT);
        return role.equals("admin") || role.equals("super_admin") || role.equals("superadmin");
    }
}
