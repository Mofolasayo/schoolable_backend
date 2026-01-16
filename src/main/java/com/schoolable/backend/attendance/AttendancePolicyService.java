package com.schoolable.backend.attendance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AttendancePolicyService {

    private final EmployeeWorkScheduleRepository employeeWorkScheduleRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final TimeOffRequestRepository timeOffRequestRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final ProfileRepository profileRepository;

    public AttendancePolicyService(
            EmployeeWorkScheduleRepository employeeWorkScheduleRepository,
            WorkScheduleRepository workScheduleRepository,
            HolidayCalendarRepository holidayCalendarRepository,
            TimeOffRequestRepository timeOffRequestRepository,
            OfficeLocationRepository officeLocationRepository,
            ProfileRepository profileRepository) {
        this.employeeWorkScheduleRepository = employeeWorkScheduleRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.timeOffRequestRepository = timeOffRequestRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.profileRepository = profileRepository;
    }

    public AttendancePolicy resolvePolicy(UUID userId, LocalDate date) {
        Profile profile = profileRepository.findById(userId).orElse(null);
        String department = profile != null ? profile.getDepartment() : null;

        WorkSchedule schedule = employeeWorkScheduleRepository.findActiveSchedule(userId, date)
            .flatMap(item -> workScheduleRepository.findById(item.getScheduleId()))
            .orElseGet(this::getDefaultSchedule);

        boolean isHoliday = !holidayCalendarRepository.findByHolidayDateAndDepartment(date, department).isEmpty()
            || !holidayCalendarRepository.findByHolidayDate(date).isEmpty();
        boolean isOnLeave = !timeOffRequestRepository.findApprovedForDate(userId, date).isEmpty();

        List<String> scheduleDays = schedule != null ? schedule.getDaysOfWeekList() : List.of();
        boolean isScheduledDay = schedule != null && matchesScheduleDay(date, scheduleDays);
        boolean remoteAllowed = schedule != null && Boolean.TRUE.equals(schedule.getRemoteAllowed());
        boolean isWorkDay;
        if (!scheduleDays.isEmpty()) {
            if (isScheduledDay) {
                isWorkDay = true;
            } else if (remoteAllowed) {
                isWorkDay = !isDefaultNonWorkingDay(date.getDayOfWeek());
            } else {
                isWorkDay = false;
            }
        } else {
            isWorkDay = !isDefaultNonWorkingDay(date.getDayOfWeek());
        }

        return new AttendancePolicy(schedule, isWorkDay, isHoliday, isOnLeave, department);
    }

    public LocationValidation validateLocation(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return new LocationValidation(false, false, -1, null, "Unknown");
        }

        List<OfficeLocation> offices = officeLocationRepository.findByIsActiveTrue();
        if (offices.isEmpty()) {
            return new LocationValidation(true, true, 0, null, "No office configured");
        }

        OfficeLocation nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (OfficeLocation office : offices) {
            double distance = haversineDistance(lat, lon, office.getLatitude(), office.getLongitude());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = office;
            }
        }

        if (nearest == null) {
            return new LocationValidation(true, false, -1, null, "Unknown");
        }

        boolean withinRadius = minDistance <= nearest.getRadiusMeters();
        return new LocationValidation(true, withinRadius, minDistance, nearest.getId(), nearest.getName());
    }

    public CheckInEvaluation evaluateCheckIn(LocalTime checkInTime, WorkSchedule schedule) {
        if (schedule == null || schedule.getStartTime() == null) {
            return new CheckInEvaluation(false, 0);
        }

        LocalTime deadline = schedule.getStartTime().plusMinutes(
            schedule.getGraceMinutes() != null ? schedule.getGraceMinutes() : 0
        );
        if (checkInTime.isAfter(deadline)) {
            int minutesLate = (int) java.time.Duration.between(deadline, checkInTime).toMinutes();
            return new CheckInEvaluation(true, minutesLate);
        }
        return new CheckInEvaluation(false, 0);
    }

    public ZoneId resolveZone(WorkSchedule schedule, UUID officeLocationId) {
        String timezone = schedule != null ? schedule.getTimezone() : null;
        if (timezone == null && officeLocationId != null) {
            timezone = officeLocationRepository.findById(officeLocationId)
                .map(OfficeLocation::getTimezone)
                .orElse(null);
        }

        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }

        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private WorkSchedule getDefaultSchedule() {
        return workScheduleRepository.findByIsActiveTrue().stream().findFirst().orElse(null);
    }

    private boolean matchesScheduleDay(LocalDate date, List<String> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return false;
        }
        DayOfWeek target = date.getDayOfWeek();
        for (String entry : daysOfWeek) {
            if (entry == null) continue;
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            String[] tokens = normalized.split(",");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) continue;
                if (matchesScheduleToken(trimmed, target)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isDefaultNonWorkingDay(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean matchesScheduleToken(String token, DayOfWeek target) {
        if (token.equals("weekday") || token.equals("weekdays") || token.equals("workday") || token.equals("workdays")) {
            return target.getValue() >= DayOfWeek.MONDAY.getValue() && target.getValue() <= DayOfWeek.FRIDAY.getValue();
        }
        if (token.equals("weekend") || token.equals("weekends")) {
            return target == DayOfWeek.SATURDAY || target == DayOfWeek.SUNDAY;
        }
        if (token.contains("-")) {
            String[] parts = token.split("-", 2);
            Integer start = resolveDayValue(parts[0]);
            Integer end = resolveDayValue(parts[1]);
            if (start == null || end == null) {
                return false;
            }
            int targetValue = target.getValue();
            if (start <= end) {
                return targetValue >= start && targetValue <= end;
            }
            return targetValue >= start || targetValue <= end;
        }

        Integer direct = resolveDayValue(token);
        return direct != null && direct == target.getValue();
    }

    private Integer resolveDayValue(String token) {
        if (token == null) return null;
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        if (normalized.chars().allMatch(Character::isDigit)) {
            try {
                int value = Integer.parseInt(normalized);
                if (value >= DayOfWeek.MONDAY.getValue() && value <= DayOfWeek.SUNDAY.getValue()) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (normalized.startsWith("mon")) return DayOfWeek.MONDAY.getValue();
        if (normalized.startsWith("tue")) return DayOfWeek.TUESDAY.getValue();
        if (normalized.startsWith("wed")) return DayOfWeek.WEDNESDAY.getValue();
        if (normalized.startsWith("thu")) return DayOfWeek.THURSDAY.getValue();
        if (normalized.startsWith("fri")) return DayOfWeek.FRIDAY.getValue();
        if (normalized.startsWith("sat")) return DayOfWeek.SATURDAY.getValue();
        if (normalized.startsWith("sun")) return DayOfWeek.SUNDAY.getValue();

        return null;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public record AttendancePolicy(
        WorkSchedule schedule,
        boolean isWorkDay,
        boolean isHoliday,
        boolean isOnLeave,
        String department
    ) {}

    public record CheckInEvaluation(boolean isLate, int minutesLate) {}

    public record LocationValidation(
        boolean isValid,
        boolean withinRadius,
        double distanceMeters,
        UUID officeId,
        String officeName
    ) {}
}
