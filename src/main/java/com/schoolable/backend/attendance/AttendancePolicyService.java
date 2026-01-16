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
        String targetName = target.name().toLowerCase(Locale.ROOT);
        String targetShort = targetName.substring(0, 3);
        String targetIndex = Integer.toString(target.getValue());

        for (String entry : daysOfWeek) {
            if (entry == null) continue;
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            if (normalized.equals(targetIndex)) return true;
            if (normalized.equals(targetName)) return true;
            if (normalized.startsWith(targetShort)) return true;
        }

        return false;
    }

    private boolean isDefaultNonWorkingDay(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
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
