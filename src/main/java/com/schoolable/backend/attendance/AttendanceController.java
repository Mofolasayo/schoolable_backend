package com.schoolable.backend.attendance;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping({"/api/attendance", "/attendance"})
@Tag(name = "Attendance", description = "Check-in/Check-out and attendance management")
public class AttendanceController {

    private final AttendanceRepository attendanceRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final ProfileRepository profileRepository;
    private final AttendancePolicyService attendancePolicyService;
    private final BiometricConsentRepository biometricConsentRepository;
    private final FaceMatchService faceMatchService;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final TimeOffRequestRepository timeOffRequestRepository;
    private final NotificationService notificationService;

    public AttendanceController(
            AttendanceRepository attendanceRepository,
            OfficeLocationRepository officeLocationRepository,
            ProfileRepository profileRepository,
            AttendancePolicyService attendancePolicyService,
            BiometricConsentRepository biometricConsentRepository,
            FaceMatchService faceMatchService,
            HolidayCalendarRepository holidayCalendarRepository,
            TimeOffRequestRepository timeOffRequestRepository,
            NotificationService notificationService) {
        this.attendanceRepository = attendanceRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.profileRepository = profileRepository;
        this.attendancePolicyService = attendancePolicyService;
        this.biometricConsentRepository = biometricConsentRepository;
        this.faceMatchService = faceMatchService;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.timeOffRequestRepository = timeOffRequestRepository;
        this.notificationService = notificationService;
    }

    // ==================== CHECK-IN ====================

    @Operation(summary = "Check in (mobile app)")
    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest req, Authentication auth, HttpServletRequest httpReq) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        LocalDate today = LocalDate.now();
        String deviceId = normalizeString(req.deviceId());
        if (deviceId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "device_id is required",
                "code", "DEVICE_ID_REQUIRED"
            ));
        }

        // Check if already checked in today
        var existingAttendance = attendanceRepository.findByUserIdAndDate(userId, today);
        if (existingAttendance.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already checked in today"));
        }

        BiometricConsent consent = biometricConsentRepository.findByUserId(userId)
            .filter(c -> c.getRevokedAt() == null)
            .orElse(null);

        if (consent == null && !Boolean.TRUE.equals(req.consentGiven())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Biometric consent required"));
        }
        if (consent == null) {
            BiometricConsent newConsent = new BiometricConsent();
            newConsent.setUserId(userId);
            if (req.consentVersion() != null) newConsent.setConsentVersion(req.consentVersion());
            if (req.retentionDays() != null) newConsent.setRetentionDays(req.retentionDays());
            consent = biometricConsentRepository.save(newConsent);
        }

        AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(userId, today);
        AttendancePolicyService.LocationValidation locationValidation = attendancePolicyService.validateLocation(req.latitude(), req.longitude());

        boolean isRemote = Boolean.TRUE.equals(req.isRemote());
        boolean remoteAllowed = policy.schedule() != null && Boolean.TRUE.equals(policy.schedule().getRemoteAllowed());
        boolean withinRadius = locationValidation.withinRadius();
        if (isRemote && !remoteAllowed) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Remote check-in is not allowed today. Please check in at the office.",
                "code", "REMOTE_NOT_ALLOWED"
            ));
        }
        if (!isRemote && !withinRadius) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Please go to the office to check in.",
                "code", "OUTSIDE_GEOFENCE"
            ));
        }

        LocalTime now = LocalTime.now();
        AttendancePolicyService.CheckInEvaluation evaluation = attendancePolicyService.evaluateCheckIn(now, policy.schedule());

        String status;
        if (evaluation.isLate()) {
            status = "late";
        } else {
            status = "present";
        }
        boolean requiresBiometric = policy.isWorkDay() && !policy.isHoliday() && !policy.isOnLeave() && !isRemote;
        if (requiresBiometric && (req.photoUrl() == null || req.photoUrl().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "photo_url is required for check-in",
                "code", "PHOTO_REQUIRED"
            ));
        }

        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile != null) {
            String registeredDeviceId = normalizeString(profile.getCheckinDeviceId());
            if (registeredDeviceId == null) {
                profile.setCheckinDeviceId(deviceId);
                profile.setCheckinDeviceInfo(req.deviceInfo());
                profile.setCheckinDeviceRegisteredAt(OffsetDateTime.now());
                profileRepository.save(profile);
            } else if (!registeredDeviceId.equals(deviceId)) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Verification failed",
                    "code", "VERIFICATION_FAILED"
                ));
            }
        }

        // Create attendance record
        Attendance attendance = new Attendance();
        attendance.setUserId(userId);
        attendance.setCheckIn(OffsetDateTime.now());
        attendance.setDate(today);
        attendance.setStatus(status);
        attendance.setIsRemote(isRemote);
        attendance.setScheduleId(policy.schedule() != null ? policy.schedule().getId() : null);
        attendance.setExpectedCheckIn(policy.schedule() != null ? policy.schedule().getStartTime() : null);
        attendance.setExpectedCheckOut(policy.schedule() != null ? policy.schedule().getEndTime() : null);
        
        // Location data
        attendance.setLatitude(req.latitude());
        attendance.setLongitude(req.longitude());
        attendance.setAccuracy(req.accuracy());
        attendance.setAddress(req.address());
        attendance.setLocation(locationValidation.officeName());
        attendance.setOfficeLocationId(locationValidation.officeId());
        attendance.setIsWithinGeofence(withinRadius);
        attendance.setDistanceMeters(locationValidation.distanceMeters());
        
        // Photo and verification
        attendance.setPhotoUrl(req.photoUrl());
        attendance.setVerificationStatus("pending");
        attendance.setFaceMatchScore(null);
        attendance.setLivenessScore(req.livenessScore());
        attendance.setLivenessType(req.livenessType());
        if (req.livenessScore() != null) {
            attendance.setLivenessPassed(req.livenessScore() >= 0.6);
        }
        if (Boolean.FALSE.equals(attendance.getLivenessPassed())) {
            attendance.setVerificationStatus("failed");
        }
        if (requiresBiometric && Boolean.FALSE.equals(attendance.getLivenessPassed())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Verification failed",
                "code", "VERIFICATION_FAILED"
            ));
        }
        
        // Device and network info
        attendance.setDeviceInfo(req.deviceInfo());
        attendance.setIpAddress(getClientIp(httpReq));
        
        attendance.setNote(req.note());
        attendance.setCreatedAt(OffsetDateTime.now());
        attendance.setRetentionUntil(OffsetDateTime.now().plusDays(consent.getRetentionDays()));

        if (requiresBiometric && req.photoUrl() != null && !Boolean.FALSE.equals(attendance.getLivenessPassed())) {
            if (profile != null) {
                if (profile.getReferenceFaceUrl() == null || profile.getReferenceFaceUrl().isBlank()) {
                    profile.setReferenceFaceUrl(req.photoUrl());
                    profile.setReferenceFaceRegisteredAt(OffsetDateTime.now());
                    profileRepository.save(profile);
                    attendance.setVerificationStatus("verified");
                    attendance.setFaceMatchScore(100.0);
                    attendance.setFaceMatchProvider("reference");
                } else {
                    FaceMatchResult matchResult = faceMatchService.compare(profile.getReferenceFaceUrl(), req.photoUrl());
                    attendance.setFaceMatchScore(matchResult.confidence());
                    attendance.setFaceMatchProvider(matchResult.provider());
                    if (!matchResult.match()) {
                        return ResponseEntity.status(403).body(Map.of(
                            "error", "Verification failed",
                            "code", "VERIFICATION_FAILED"
                        ));
                    }
                    attendance.setVerificationStatus("verified");
                }
            }
        }

        attendanceRepository.save(attendance);

        // Build response
        Map<String, Object> response = buildAttendanceResponse(attendance);
        response.put("location_validated", locationValidation.isValid());
        response.put("within_office", withinRadius);
        response.put("distance_meters", locationValidation.distanceMeters());
        
        // If location is outside office radius, flag it
        if (!withinRadius && !isRemote) {
            attendance.setNote((attendance.getNote() != null ? attendance.getNote() + ". " : "") + 
                "Warning: Check-in from outside office radius (" + Math.round(locationValidation.distanceMeters()) + "m away)");
            attendance.setVerificationStatus("flagged");
            attendanceRepository.save(attendance);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== CHECK-OUT ====================

    @Operation(summary = "Check out (mobile app)")
    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@RequestBody CheckOutRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        LocalDate today = LocalDate.now();

        // Find today's attendance
        var attendanceOpt = attendanceRepository.findByUserIdAndDate(userId, today);
        if (attendanceOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No check-in found for today"));
        }

        Attendance attendance = attendanceOpt.get();
        if (attendance.getCheckOut() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already checked out today"));
        }

        // Update check-out
        attendance.setCheckOut(OffsetDateTime.now());
        if (req.note() != null && !req.note().isEmpty()) {
            String existingNote = attendance.getNote() != null ? attendance.getNote() + ". " : "";
            attendance.setNote(existingNote + "Check-out: " + req.note());
        }

        attendanceRepository.save(attendance);
        return ResponseEntity.ok(buildAttendanceResponse(attendance));
    }

    // ==================== GET ATTENDANCE ====================

    @Operation(summary = "Get today's attendance for current user")
    @GetMapping("/today")
    public ResponseEntity<?> getTodayAttendance(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        
        var attendanceOpt = attendanceRepository.findByUserIdAndDate(userId, LocalDate.now());
        if (attendanceOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("checked_in", false));
        }

        Map<String, Object> response = buildAttendanceResponse(attendanceOpt.get());
        response.put("checked_in", true);
        response.put("checked_out", attendanceOpt.get().getCheckOut() != null);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get attendance history for current user")
    @GetMapping("/history")
    public ResponseEntity<?> getAttendanceHistory(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        
        List<Attendance> records = attendanceRepository.findByUserIdOrderByDateDesc(userId);
        List<Map<String, Object>> response = records.stream()
                .map(this::buildAttendanceResponse)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all attendance records for today (admin/dashboard)")
    @GetMapping("/all/today")
    public ResponseEntity<?> getAllAttendanceToday(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        List<Attendance> records = attendanceRepository.findByDateOrderByCheckInDesc(LocalDate.now());
        List<Map<String, Object>> response = records.stream()
                .map(this::buildAttendanceResponseWithUser)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get attendance metrics for today (dashboard)")
    @GetMapping("/metrics/today")
    public ResponseEntity<?> getTodayMetrics(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        LocalDate today = LocalDate.now();
        long present = attendanceRepository.countByDateAndStatus(today, "present");
        long totalLate = attendanceRepository.countByDateAndStatusIn(today, List.of("late", "excused"));
        long absent = attendanceRepository.countByDateAndStatus(today, "absent");

        // Get total expected staff count (excluding admins and non-workdays)
        List<Profile> staff = profileRepository.findByRoleNot("admin");
        long totalStaff = staff.stream().filter(profile -> {
            AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(profile.getId(), today);
            return policy.isWorkDay() && !policy.isHoliday() && !policy.isOnLeave();
        }).count();
        long checkedIn = present + totalLate;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("date", today.toString());
        metrics.put("present", present);
        metrics.put("late", totalLate);
        metrics.put("absent", absent);
        metrics.put("excused", 0);
        metrics.put("total_checked_in", checkedIn);
        metrics.put("total_staff", totalStaff);
        metrics.put("pending", Math.max(0, totalStaff - checkedIn));
        metrics.put("attendance_rate", totalStaff > 0 ? Math.round((double) checkedIn / totalStaff * 100) : 0);

        return ResponseEntity.ok(metrics);
    }

    @Operation(summary = "Get attendance by date range (dashboard)")
    @GetMapping("/range")
    public ResponseEntity<?> getAttendanceByRange(
            @RequestParam String startDate, 
            @RequestParam String endDate,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        
        List<Attendance> records = attendanceRepository.findByDateRange(start, end);
        Map<String, Attendance> recordIndex = new HashMap<>();
        for (Attendance record : records) {
            if (record.getUserId() == null || record.getDate() == null) {
                continue;
            }
            recordIndex.put(record.getUserId() + "|" + record.getDate(), record);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Attendance record : records) {
            response.add(buildAttendanceResponseWithUser(record));
        }

        List<Profile> staff = profileRepository.findByRoleNot("admin");
        if (staff != null && !staff.isEmpty()) {
            long syntheticId = -1;
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                for (Profile profile : staff) {
                    if (profile.getRole() != null) {
                        String role = profile.getRole().toLowerCase(Locale.ROOT);
                        if (role.equals("super_admin") || role.equals("superadmin")) {
                            continue;
                        }
                    }
                    AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(profile.getId(), cursor);
                    if (!policy.isWorkDay() || policy.isHoliday() || policy.isOnLeave()) {
                        continue;
                    }
                    String key = profile.getId() + "|" + cursor;
                    if (recordIndex.containsKey(key)) {
                        continue;
                    }

                    Map<String, Object> absent = new HashMap<>();
                    absent.put("id", syntheticId--);
                    absent.put("user_id", profile.getId());
                    absent.put("check_in", null);
                    absent.put("check_out", null);
                    absent.put("date", cursor);
                    absent.put("status", "absent");
                    absent.put("location", null);
                    absent.put("address", null);
                    absent.put("latitude", null);
                    absent.put("longitude", null);
                    absent.put("accuracy", null);
                    absent.put("photo_url", null);
                    absent.put("is_remote", null);
                    absent.put("office_location_id", null);
                    absent.put("schedule_id", policy.schedule() != null ? policy.schedule().getId() : null);
                    absent.put("expected_check_in", policy.schedule() != null ? policy.schedule().getStartTime() : null);
                    absent.put("expected_check_out", policy.schedule() != null ? policy.schedule().getEndTime() : null);
                    absent.put("face_match_score", null);
                    absent.put("verification_status", null);
                    absent.put("is_within_geofence", null);
                    absent.put("distance_meters", null);
                    absent.put("liveness_score", null);
                    absent.put("liveness_type", null);
                    absent.put("liveness_passed", null);
                    absent.put("face_match_provider", null);
                    Map<String, Object> photo = new LinkedHashMap<>();
                    photo.put("url", null);
                    photo.put("verification_status", null);
                    photo.put("face_match_score", null);
                    photo.put("face_match_provider", null);
                    photo.put("liveness_score", null);
                    photo.put("liveness_type", null);
                    photo.put("liveness_passed", null);
                    photo.put("retention_until", null);
                    absent.put("photo", photo);
                    absent.put("note", "Auto-marked absent");

                    Map<String, Object> user = new HashMap<>();
                    user.put("id", profile.getId());
                    user.put("full_name", profile.getFullName());
                    user.put("email", profile.getEmail());
                    user.put("department", profile.getDepartment());
                    user.put("job_title", profile.getJobTitle());
                    user.put("avatar_url", getAvatarUrl(profile));
                    absent.put("user", user);

                    response.add(absent);
                }
                cursor = cursor.plusDays(1);
            }
        }

        response.sort((a, b) -> {
            LocalDate dateA = LocalDate.parse(String.valueOf(a.get("date")));
            LocalDate dateB = LocalDate.parse(String.valueOf(b.get("date")));
            int dateCompare = dateB.compareTo(dateA);
            if (dateCompare != 0) {
                return dateCompare;
            }
            OffsetDateTime checkInA = a.get("check_in") != null ? OffsetDateTime.parse(String.valueOf(a.get("check_in"))) : OffsetDateTime.MIN;
            OffsetDateTime checkInB = b.get("check_in") != null ? OffsetDateTime.parse(String.valueOf(b.get("check_in"))) : OffsetDateTime.MIN;
            return checkInB.compareTo(checkInA);
        });
        
        return ResponseEntity.ok(response);
    }

    // ==================== FACE VERIFICATION (Webhook/Callback) ====================

    @Operation(summary = "Update face verification result (called by external service)")
    @PostMapping("/{id}/verify")
    public ResponseEntity<?> updateVerification(
            @PathVariable Long id,
            @RequestBody VerificationResult req,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        var attendanceOpt = attendanceRepository.findById(id);
        if (attendanceOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Attendance record not found"));
        }

        Attendance attendance = attendanceOpt.get();
        attendance.setFaceMatchScore(req.matchScore());
        attendance.setVerificationStatus(req.verified() ? "verified" : "failed");
        
        if (req.note() != null) {
            String existingNote = attendance.getNote() != null ? attendance.getNote() + ". " : "";
            attendance.setNote(existingNote + req.note());
        }

        attendanceRepository.save(attendance);
        return ResponseEntity.ok(buildAttendanceResponse(attendance));
    }

    // ==================== FACE REFERENCE ====================

    @Operation(summary = "Get current user's reference face")
    @GetMapping("/reference-face")
    public ResponseEntity<?> getReferenceFace(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Profile profile = profileOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("has_reference", profile.getReferenceFaceUrl() != null);
        response.put("reference_url", profile.getReferenceFaceUrl());
        response.put("registered_at", profile.getReferenceFaceRegisteredAt());
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Register reference face")
    @PostMapping("/reference-face")
    public ResponseEntity<?> registerReferenceFace(@RequestBody Map<String, String> payload, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal(); // Correctly cast to UUID since security config uses UUID
        String faceUrl = payload.get("photo_url");
        
        if (faceUrl == null || faceUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "photo_url is required"));
        }

        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Profile profile = profileOpt.get();
        if (profile.getReferenceFaceUrl() != null && !profile.getReferenceFaceUrl().isBlank()) {
            return ResponseEntity.status(409).body(Map.of(
                "error", "Reference face already registered. Contact admin to reset.",
                "code", "REFERENCE_FACE_ALREADY_SET"
            ));
        }
        profile.setReferenceFaceUrl(faceUrl);
        profile.setReferenceFaceRegisteredAt(OffsetDateTime.now());
        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Reference face registered successfully",
            "reference_url", faceUrl
        ));
    }

    @Operation(summary = "Compare face (Mock implementation)")
    @PostMapping("/compare-faces")
    public ResponseEntity<?> compareFaces(@RequestBody Map<String, String> payload, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        String currentFaceUrl = payload.get("face_url");
        if (currentFaceUrl == null || currentFaceUrl.isEmpty()) {
            currentFaceUrl = payload.get("check_in_photo_url");
        }
        
        if (currentFaceUrl == null || currentFaceUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "face_url is required"));
        }

        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Profile profile = profileOpt.get();
        if (profile.getReferenceFaceUrl() == null) {
             return ResponseEntity.badRequest().body(Map.of(
                 "error", "No reference face found. Please register a face first.",
                 "code", "NO_REFERENCE_FACE"
             ));
        }

        FaceMatchResult result = faceMatchService.compare(profile.getReferenceFaceUrl(), currentFaceUrl);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("match", result.match());
        response.put("confidence", result.confidence());
        response.put("verified", result.match());
        response.put("provider", result.provider());
        response.put("message", result.message());
        return ResponseEntity.ok(response);
    }

    // ==================== OFFICE LOCATIONS ====================

    @Operation(summary = "Get all office locations")
    @GetMapping("/offices")
    public ResponseEntity<?> getOfficeLocations(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        List<OfficeLocation> offices = officeLocationRepository.findByIsActiveTrue();
        return ResponseEntity.ok(offices.stream().map(o -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", o.getId());
            item.put("name", o.getName());
            item.put("address", o.getAddress());
            item.put("latitude", o.getLatitude());
            item.put("longitude", o.getLongitude());
            item.put("radius_meters", o.getRadiusMeters());
            item.put("timezone", o.getTimezone());
            return item;
        }).toList());
    }

    @Operation(summary = "Get today's attendance policy for current user")
    @GetMapping("/policy/today")
    public ResponseEntity<?> getTodayPolicy(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        LocalDate today = LocalDate.now();

        AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(userId, today);
        WorkSchedule schedule = policy.schedule();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", today.toString());
        response.put("department", policy.department());
        response.put("isWorkDay", policy.isWorkDay());
        response.put("isHoliday", policy.isHoliday());
        response.put("isOnLeave", policy.isOnLeave());
        response.put("holidayName", policy.holidayName());

        if (schedule != null) {
            Map<String, Object> scheduleDto = new LinkedHashMap<>();
            scheduleDto.put("id", schedule.getId());
            scheduleDto.put("name", schedule.getName());
            scheduleDto.put("startTime", schedule.getStartTime());
            scheduleDto.put("endTime", schedule.getEndTime());
            scheduleDto.put("graceMinutes", schedule.getGraceMinutes());
            scheduleDto.put("timezone", schedule.getTimezone());
            scheduleDto.put("remoteAllowed", schedule.getRemoteAllowed());
            scheduleDto.put("daysOfWeek", schedule.getDaysOfWeekList());
            response.put("schedule", scheduleDto);
        } else {
            response.put("schedule", null);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== HOLIDAY CALENDAR ====================

    @Operation(summary = "List public holidays")
    @GetMapping("/holidays")
    public ResponseEntity<?> getHolidays(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String department,
            Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        LocalDate start = null;
        LocalDate end = null;
        try {
            if (startDate != null && !startDate.isBlank()) {
                start = LocalDate.parse(startDate);
            }
            if (endDate != null && !endDate.isBlank()) {
                end = LocalDate.parse(endDate);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date range"));
        }

        if (start != null && end == null) {
            end = start;
        }
        if (end != null && start == null) {
            start = end;
        }

        List<HolidayCalendar> holidays;
        if (start != null && end != null) {
            if (department != null && !department.isBlank()) {
                holidays = holidayCalendarRepository.findByHolidayDateBetweenAndDepartment(start, end, department);
            } else {
                holidays = holidayCalendarRepository.findByHolidayDateBetween(start, end);
            }
        } else if (department != null && !department.isBlank()) {
            holidays = holidayCalendarRepository.findAll().stream()
                    .filter(item -> department.equalsIgnoreCase(item.getDepartment()))
                    .toList();
        } else {
            holidays = holidayCalendarRepository.findAll();
        }

        holidays = holidays.stream()
                .sorted(Comparator.comparing(HolidayCalendar::getHolidayDate))
                .toList();

        return ResponseEntity.ok(holidays.stream().map(item -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", item.getId());
            dto.put("holiday_date", item.getHolidayDate() != null ? item.getHolidayDate().toString() : null);
            dto.put("name", item.getName());
            dto.put("department", item.getDepartment());
            dto.put("region", item.getRegion());
            dto.put("is_paid", item.getIsPaid());
            return dto;
        }).toList());
    }

    @Operation(summary = "Create a public holiday")
    @PostMapping("/holidays")
    public ResponseEntity<?> createHoliday(@RequestBody HolidayRequest req, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Holiday request is required"));
        }
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Holiday name is required"));
        }

        String startDateRaw = req.startDate() != null ? req.startDate() : req.holidayDate();
        String endDateRaw = req.endDate() != null ? req.endDate() : req.holidayDate();

        if (startDateRaw == null || startDateRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Holiday start date is required"));
        }

        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(startDateRaw);
            endDate = (endDateRaw == null || endDateRaw.isBlank())
                    ? startDate
                    : LocalDate.parse(endDateRaw);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid holiday date range"));
        }

        if (endDate.isBefore(startDate)) {
            LocalDate swap = startDate;
            startDate = endDate;
            endDate = swap;
        }

        String department = req.department() != null && !req.department().isBlank()
                ? req.department().trim()
                : null;

        List<Map<String, Object>> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
            boolean exists;
            if (department != null) {
                exists = !holidayCalendarRepository.findByHolidayDateAndDepartment(cursor, department).isEmpty();
            } else {
                exists = !holidayCalendarRepository.findByHolidayDate(cursor).isEmpty();
            }
            if (exists) {
                skipped.add(cursor.toString());
                continue;
            }

            HolidayCalendar holiday = new HolidayCalendar();
            holiday.setHolidayDate(cursor);
            holiday.setName(req.name().trim());
            holiday.setDepartment(department);
            if (req.region() != null && !req.region().isBlank()) {
                holiday.setRegion(req.region().trim());
            }
            if (req.isPaid() != null) {
                holiday.setIsPaid(req.isPaid());
            }

            HolidayCalendar saved = holidayCalendarRepository.save(holiday);
            Map<String, Object> createdItem = new LinkedHashMap<>();
            createdItem.put("id", saved.getId());
            createdItem.put("holiday_date", saved.getHolidayDate() != null ? saved.getHolidayDate().toString() : null);
            createdItem.put("name", saved.getName());
            createdItem.put("department", saved.getDepartment());
            createdItem.put("region", saved.getRegion());
            createdItem.put("is_paid", saved.getIsPaid());
            created.add(createdItem);
        }

        if (created.isEmpty() && !skipped.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Holidays already exist for the selected range",
                    "skipped", skipped
            ));
        }

        return ResponseEntity.ok(Map.of(
                "created", created,
                "skipped", skipped,
                "totalCreated", created.size()
        ));
    }

    @Operation(summary = "List approved time off requests")
    @GetMapping("/time-off")
    public ResponseEntity<?> getApprovedTimeOff(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String department,
            Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date range"));
        }

        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }

        List<TimeOffRequest> requests = timeOffRequestRepository.findApprovedOverlappingRange(start, end);
        Map<UUID, Profile> profiles = new HashMap<>();
        List<Map<String, Object>> response = new ArrayList<>();

        for (TimeOffRequest request : requests) {
            Profile profile = profiles.computeIfAbsent(request.getEmployeeId(), id ->
                    profileRepository.findById(id).orElse(null));

            if (department != null && profile != null && profile.getDepartment() != null
                    && !department.equalsIgnoreCase(profile.getDepartment())) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", request.getId());
            item.put("employeeId", request.getEmployeeId());
            item.put("employeeName", profile != null ? profile.getFullName() : null);
            item.put("department", profile != null ? profile.getDepartment() : null);
            item.put("type", request.getType());
            item.put("startDate", request.getStartDate() != null ? request.getStartDate().toString() : null);
            item.put("endDate", request.getEndDate() != null ? request.getEndDate().toString() : null);
            item.put("notes", request.getNotes());
            item.put("approvedAt", request.getApprovedAt() != null ? request.getApprovedAt().toString() : null);
            response.add(item);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== TIME OFF REQUESTS ====================

    @Operation(summary = "Submit a time off request (mobile)")
    @PostMapping("/time-off/requests")
    public ResponseEntity<?> createTimeOffRequest(@RequestBody TimeOffRequestPayload req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        if (req.startDate() == null || req.startDate().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "start_date is required"));
        }
        if (req.endDate() == null || req.endDate().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "end_date is required"));
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(req.startDate());
            end = LocalDate.parse(req.endDate());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date range"));
        }
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }

        List<TimeOffRequest> overlapping = timeOffRequestRepository.findActiveOverlappingForEmployee(userId, start, end);
        if (!overlapping.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                "error", "You already have a pending or approved leave within this range"
            ));
        }

        TimeOffRequest request = new TimeOffRequest();
        request.setEmployeeId(userId);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setType(req.type() != null && !req.type().isBlank() ? req.type().trim() : "leave");
        if (req.notes() != null && !req.notes().isBlank()) {
            request.setNotes(req.notes().trim());
        }

        TimeOffRequest saved = timeOffRequestRepository.save(request);
        Profile profile = profileRepository.findById(userId).orElse(null);

        notifyAdminsTimeOffRequest(saved, profile);

        return ResponseEntity.ok(buildTimeOffResponse(saved, profile));
    }

    @Operation(summary = "List current user's time off requests (mobile)")
    @GetMapping("/time-off/requests")
    public ResponseEntity<?> getMyTimeOffRequests(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        List<TimeOffRequest> requests = timeOffRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(userId);
        Profile profile = profileRepository.findById(userId).orElse(null);
        List<Map<String, Object>> response = requests.stream()
            .map(item -> buildTimeOffResponse(item, profile))
            .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List time off requests (admin/dashboard)")
    @GetMapping("/time-off/requests/all")
    public ResponseEntity<?> getAllTimeOffRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        TimeOffRequest.Status statusFilter = parseTimeOffStatus(status);
        List<TimeOffRequest> requests = statusFilter != null
            ? timeOffRequestRepository.findByStatusOrderByCreatedAtDesc(statusFilter)
            : timeOffRequestRepository.findAllByOrderByCreatedAtDesc();

        LocalDate start = null;
        LocalDate end = null;
        try {
            if (startDate != null && !startDate.isBlank()) {
                start = LocalDate.parse(startDate);
            }
            if (endDate != null && !endDate.isBlank()) {
                end = LocalDate.parse(endDate);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date range"));
        }

        Map<UUID, Profile> profiles = new HashMap<>();
        List<Map<String, Object>> response = new ArrayList<>();
        for (TimeOffRequest request : requests) {
            if (start != null && request.getEndDate() != null && request.getEndDate().isBefore(start)) {
                continue;
            }
            if (end != null && request.getStartDate() != null && request.getStartDate().isAfter(end)) {
                continue;
            }

            Profile profile = profiles.computeIfAbsent(request.getEmployeeId(), id ->
                profileRepository.findById(id).orElse(null));

            if (department != null && !department.isBlank() && profile != null && profile.getDepartment() != null
                && !department.equalsIgnoreCase(profile.getDepartment())) {
                continue;
            }

            response.add(buildTimeOffResponse(request, profile));
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Approve or reject a time off request (admin)")
    @PostMapping("/time-off/requests/{id}/decision")
    public ResponseEntity<?> reviewTimeOffRequest(
            @PathVariable UUID id,
            @RequestBody TimeOffDecision req,
            Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        if (req == null || req.status() == null || req.status().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        TimeOffRequest.Status status = parseTimeOffStatus(req.status());
        if (status == null || status == TimeOffRequest.Status.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
        }

        Optional<TimeOffRequest> requestOpt = timeOffRequestRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Time off request not found"));
        }

        TimeOffRequest request = requestOpt.get();
        if (request.getStatus() != TimeOffRequest.Status.PENDING) {
            return ResponseEntity.status(409).body(Map.of("error", "Request already processed"));
        }

        request.setStatus(status);
        if (status == TimeOffRequest.Status.APPROVED) {
            request.setApprovedAt(OffsetDateTime.now());
            if (auth.getPrincipal() instanceof UUID adminId) {
                request.setApprovedBy(adminId);
            }
        }
        if (req.reviewNotes() != null && !req.reviewNotes().isBlank()) {
            String existing = request.getNotes() != null ? request.getNotes() + "\n\n" : "";
            request.setNotes(existing + "Admin note: " + req.reviewNotes().trim());
        }

        TimeOffRequest saved = timeOffRequestRepository.save(request);
        Profile profile = profileRepository.findById(saved.getEmployeeId()).orElse(null);

        notifyEmployeeTimeOffDecision(saved, profile);

        return ResponseEntity.ok(buildTimeOffResponse(saved, profile));
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> buildAttendanceResponse(Attendance a) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", a.getId());
        response.put("user_id", a.getUserId());
        response.put("check_in", a.getCheckIn());
        response.put("check_out", a.getCheckOut());
        response.put("date", a.getDate());
        String status = a.getStatus();
        if ("excused".equalsIgnoreCase(status)) {
            status = "late";
        }
        response.put("status", status);
        response.put("location", a.getLocation());
        response.put("address", a.getAddress());
        response.put("latitude", a.getLatitude());
        response.put("longitude", a.getLongitude());
        response.put("accuracy", a.getAccuracy());
        response.put("photo_url", a.getPhotoUrl());
        response.put("is_remote", a.getIsRemote());
        response.put("office_location_id", a.getOfficeLocationId());
        response.put("schedule_id", a.getScheduleId());
        response.put("expected_check_in", a.getExpectedCheckIn());
        response.put("expected_check_out", a.getExpectedCheckOut());
        response.put("face_match_score", a.getFaceMatchScore());
        response.put("verification_status", a.getVerificationStatus());
        response.put("is_within_geofence", a.getIsWithinGeofence());
        response.put("distance_meters", a.getDistanceMeters());
        response.put("liveness_score", a.getLivenessScore());
        response.put("liveness_type", a.getLivenessType());
        response.put("liveness_passed", a.getLivenessPassed());
        response.put("face_match_provider", a.getFaceMatchProvider());
        response.put("note", a.getNote());
        Map<String, Object> photo = new LinkedHashMap<>();
        photo.put("url", a.getPhotoUrl());
        photo.put("verification_status", a.getVerificationStatus());
        photo.put("face_match_score", a.getFaceMatchScore());
        photo.put("face_match_provider", a.getFaceMatchProvider());
        photo.put("liveness_score", a.getLivenessScore());
        photo.put("liveness_type", a.getLivenessType());
        photo.put("liveness_passed", a.getLivenessPassed());
        photo.put("retention_until", a.getRetentionUntil());
        response.put("photo", photo);
        return response;
    }

    private Map<String, Object> buildAttendanceResponseWithUser(Attendance a) {
        Map<String, Object> response = buildAttendanceResponse(a);
        
        // Add user info
        if (a.getUserId() != null) {
            var profileOpt = profileRepository.findById(a.getUserId());
            if (profileOpt.isPresent()) {
                Profile p = profileOpt.get();
                Map<String, Object> user = new HashMap<>();
                user.put("id", p.getId());
                user.put("full_name", p.getFullName());
                user.put("email", p.getEmail());
                user.put("department", p.getDepartment());
                user.put("job_title", p.getJobTitle());
                user.put("avatar_url", getAvatarUrl(p));
                response.put("user", user);
            }
        }
        return response;
    }

    private Map<String, Object> buildTimeOffResponse(TimeOffRequest request, Profile profile) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", request.getId());
        item.put("employeeId", request.getEmployeeId());
        item.put("employeeName", profile != null ? profile.getFullName() : null);
        item.put("department", profile != null ? profile.getDepartment() : null);
        item.put("type", request.getType());
        item.put("startDate", request.getStartDate() != null ? request.getStartDate().toString() : null);
        item.put("endDate", request.getEndDate() != null ? request.getEndDate().toString() : null);
        item.put("notes", request.getNotes());
        item.put("status", request.getStatus() != null ? request.getStatus().name().toLowerCase(Locale.ROOT) : null);
        item.put("createdAt", request.getCreatedAt() != null ? request.getCreatedAt().toString() : null);
        item.put("approvedAt", request.getApprovedAt() != null ? request.getApprovedAt().toString() : null);
        item.put("approvedBy", request.getApprovedBy());
        return item;
    }

    private TimeOffRequest.Status parseTimeOffStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return TimeOffRequest.Status.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String getAvatarUrl(Profile p) {
        if (p.getAvatarUrl() != null && !p.getAvatarUrl().isEmpty()) {
            return p.getAvatarUrl();
        }
        String style = "bottts";
        if (p.getGender() != null) {
            if (p.getGender().equalsIgnoreCase("male")) style = "adventurer";
            else if (p.getGender().equalsIgnoreCase("female")) style = "adventurer-neutral";
        }
        String seed = p.getEmployeeId() != null ? p.getEmployeeId() : 
                (p.getEmail() != null ? p.getEmail() : "User");
        return "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private void notifyAdminsTimeOffRequest(TimeOffRequest request, Profile profile) {
        List<UUID> adminIds = profileRepository.findAll().stream()
            .filter(p -> isAdminRole(p.getRole()))
            .filter(p -> isActiveStatus(p.getStatus()))
            .map(Profile::getId)
            .toList();

        if (adminIds.isEmpty()) {
            return;
        }

        String title = "Leave Request";
        String requester = profile != null && profile.getFullName() != null ? profile.getFullName() : "An employee";
        String body = requester + " submitted a leave request.";
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.getId().toString());
        data.put("action", "review_leave");

        notificationService.sendToUsers(
            adminIds,
            title,
            body,
            "LEAVE",
            request.getId().toString(),
            data
        );
    }

    private void notifyEmployeeTimeOffDecision(TimeOffRequest request, Profile profile) {
        if (request.getEmployeeId() == null || request.getStatus() == null) {
            return;
        }

        String title = "Leave Update";
        String status = request.getStatus().name().toLowerCase(Locale.ROOT);
        String body = "Your leave request was " + status + ".";
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.getId().toString());
        data.put("action", "open_leave");

        notificationService.sendToUser(
            request.getEmployeeId(),
            title,
            body,
            "LEAVE",
            request.getId().toString(),
            data
        );
    }

    private boolean isAdminRole(String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("admin")
            || normalized.equals("super_admin")
            || normalized.equals("super admin")
            || normalized.equals("superadmin");
    }

    private boolean isActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalized = status.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("active") || normalized.equals("approved");
    }

    private String normalizeString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ==================== REQUEST/RESPONSE RECORDS ====================

    public record CheckInRequest(
            Double latitude,
            Double longitude,
            Double accuracy,
            String address,
            @JsonAlias({"photo_url", "face_url", "check_in_photo_url"})
            String photoUrl,
            @JsonAlias({"device_info"})
            String deviceInfo,
            @JsonAlias({"device_id", "deviceId"})
            String deviceId,
            String note,
            @JsonAlias({"is_remote"})
            Boolean isRemote,
            @JsonAlias({"liveness_score"})
            Double livenessScore,
            @JsonAlias({"liveness_type"})
            String livenessType,
            @JsonAlias({"consent_given"})
            Boolean consentGiven,
            @JsonAlias({"consent_version"})
            String consentVersion,
            @JsonAlias({"retention_days"})
            Integer retentionDays
    ) {}

    public record CheckOutRequest(String note) {}

    public record VerificationResult(
            boolean verified,
            Double matchScore,
            String note
    ) {}

    public record HolidayRequest(
            @JsonAlias({"holiday_date", "date"})
            String holidayDate,
            @JsonAlias({"start_date", "startDate"})
            String startDate,
            @JsonAlias({"end_date", "endDate"})
            String endDate,
            String name,
            String department,
            String region,
            @JsonAlias({"is_paid", "isPaid"})
            Boolean isPaid
    ) {}

    public record TimeOffRequestPayload(
            @JsonAlias({"start_date", "startDate"})
            String startDate,
            @JsonAlias({"end_date", "endDate"})
            String endDate,
            String type,
            String notes
    ) {}

    public record TimeOffDecision(
            String status,
            @JsonAlias({"review_notes", "reviewNotes"})
            String reviewNotes
    ) {}

}
