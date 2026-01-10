package com.schoolable.backend.attendance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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

        boolean isWorkDay = schedule != null && schedule.getDaysOfWeekList().contains(date.getDayOfWeek().name());

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
