package com.schoolable.backend.attendance;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    public AttendanceController(
            AttendanceRepository attendanceRepository,
            OfficeLocationRepository officeLocationRepository,
            ProfileRepository profileRepository,
            AttendancePolicyService attendancePolicyService,
            BiometricConsentRepository biometricConsentRepository,
            FaceMatchService faceMatchService) {
        this.attendanceRepository = attendanceRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.profileRepository = profileRepository;
        this.attendancePolicyService = attendancePolicyService;
        this.biometricConsentRepository = biometricConsentRepository;
        this.faceMatchService = faceMatchService;
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
        if (isRemote && remoteAllowed) {
            withinRadius = true;
        }

        LocalTime now = LocalTime.now();
        AttendancePolicyService.CheckInEvaluation evaluation = attendancePolicyService.evaluateCheckIn(now, policy.schedule());

        String status;
        if (policy.isHoliday() || policy.isOnLeave() || !policy.isWorkDay()) {
            status = "excused";
        } else if (evaluation.isLate()) {
            status = "late";
        } else {
            status = "present";
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
        
        // Device and network info
        attendance.setDeviceInfo(req.deviceInfo());
        attendance.setIpAddress(getClientIp(httpReq));
        
        attendance.setNote(req.note());
        attendance.setCreatedAt(OffsetDateTime.now());
        attendance.setRetentionUntil(OffsetDateTime.now().plusDays(consent.getRetentionDays()));

        if (req.photoUrl() != null && policy.isWorkDay() && !policy.isHoliday() && !policy.isOnLeave() && !Boolean.FALSE.equals(attendance.getLivenessPassed())) {
            Profile profile = profileRepository.findById(userId).orElse(null);
            if (profile != null && profile.getReferenceFaceUrl() != null) {
                FaceMatchResult matchResult = faceMatchService.compare(profile.getReferenceFaceUrl(), req.photoUrl());
                attendance.setFaceMatchScore(matchResult.confidence());
                attendance.setVerificationStatus(matchResult.match() ? "verified" : "failed");
                attendance.setFaceMatchProvider(matchResult.provider());
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
        long late = attendanceRepository.countByDateAndStatus(today, "late");
        long absent = attendanceRepository.countByDateAndStatus(today, "absent");
        long excused = attendanceRepository.countByDateAndStatus(today, "excused");

        // Get total expected staff count (excluding admins and excused days)
        List<Profile> staff = profileRepository.findByRoleNot("admin");
        long totalStaff = staff.stream().filter(profile -> {
            AttendancePolicyService.AttendancePolicy policy = attendancePolicyService.resolvePolicy(profile.getId(), today);
            return policy.isWorkDay() && !policy.isHoliday() && !policy.isOnLeave();
        }).count();
        long checkedIn = present + late;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("date", today.toString());
        metrics.put("present", present);
        metrics.put("late", late);
        metrics.put("absent", absent);
        metrics.put("excused", excused);
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

        return ResponseEntity.ok(Map.of(
            "match", result.match(),
            "confidence", result.confidence(),
            "verified", result.match(),
            "provider", result.provider(),
            "message", result.message()
        ));
    }

    // ==================== OFFICE LOCATIONS ====================

    @Operation(summary = "Get all office locations")
    @GetMapping("/offices")
    public ResponseEntity<?> getOfficeLocations(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        List<OfficeLocation> offices = officeLocationRepository.findByIsActiveTrue();
        return ResponseEntity.ok(offices.stream().map(o -> Map.of(
                "id", o.getId(),
                "name", o.getName(),
                "address", o.getAddress(),
                "latitude", o.getLatitude(),
                "longitude", o.getLongitude(),
                "radius_meters", o.getRadiusMeters(),
                "timezone", o.getTimezone()
        )).toList());
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

    // ==================== HELPER METHODS ====================

    private Map<String, Object> buildAttendanceResponse(Attendance a) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", a.getId());
        response.put("user_id", a.getUserId());
        response.put("check_in", a.getCheckIn());
        response.put("check_out", a.getCheckOut());
        response.put("date", a.getDate());
        response.put("status", a.getStatus());
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

}
