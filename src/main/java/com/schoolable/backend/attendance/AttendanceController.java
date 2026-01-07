package com.schoolable.backend.attendance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/attendance")
@Tag(name = "Attendance", description = "Check-in/Check-out and attendance management")
public class AttendanceController {

    private final AttendanceRepository attendanceRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final ProfileRepository profileRepository;
    
    // Default check-in deadline (9:00 AM)
    private static final LocalTime CHECK_IN_DEADLINE = LocalTime.of(9, 0);

    public AttendanceController(
            AttendanceRepository attendanceRepository,
            OfficeLocationRepository officeLocationRepository,
            ProfileRepository profileRepository) {
        this.attendanceRepository = attendanceRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.profileRepository = profileRepository;
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

        // Validate location (geo-fencing)
        LocationValidation locationValidation = validateLocation(req.latitude(), req.longitude());

        // Determine status based on time
        LocalTime now = LocalTime.now();
        String status = now.isAfter(CHECK_IN_DEADLINE) ? "late" : "present";

        // Create attendance record
        Attendance attendance = new Attendance();
        attendance.setUserId(userId);
        attendance.setCheckIn(OffsetDateTime.now());
        attendance.setDate(today);
        attendance.setStatus(status);
        
        // Location data
        attendance.setLatitude(req.latitude());
        attendance.setLongitude(req.longitude());
        attendance.setAccuracy(req.accuracy());
        attendance.setAddress(req.address());
        attendance.setLocation(locationValidation.officeName);
        
        // Photo and verification
        attendance.setPhotoUrl(req.photoUrl());
        attendance.setVerificationStatus("pending"); // Will be updated by face recognition service
        attendance.setFaceMatchScore(null); // To be set after verification
        
        // Device and network info
        attendance.setDeviceInfo(req.deviceInfo());
        attendance.setIpAddress(getClientIp(httpReq));
        
        attendance.setNote(req.note());
        attendance.setCreatedAt(OffsetDateTime.now());

        attendanceRepository.save(attendance);

        // Build response
        Map<String, Object> response = buildAttendanceResponse(attendance);
        response.put("location_validated", locationValidation.isValid);
        response.put("within_office", locationValidation.withinRadius);
        response.put("distance_meters", locationValidation.distanceMeters);
        
        // If location is outside office radius, flag it
        if (!locationValidation.withinRadius) {
            attendance.setNote((attendance.getNote() != null ? attendance.getNote() + ". " : "") + 
                "Warning: Check-in from outside office radius (" + Math.round(locationValidation.distanceMeters) + "m away)");
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

        LocalDate today = LocalDate.now();
        long present = attendanceRepository.countByDateAndStatus(today, "present");
        long late = attendanceRepository.countByDateAndStatus(today, "late");
        long absent = attendanceRepository.countByDateAndStatus(today, "absent");
        long excused = attendanceRepository.countByDateAndStatus(today, "excused");

        // Get total staff count (excluding admins)
        long totalStaff = profileRepository.findByRoleNot("admin").size();
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

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        
        List<Attendance> records = attendanceRepository.findByDateRange(start, end);
        List<Map<String, Object>> response = records.stream()
                .map(this::buildAttendanceResponseWithUser)
                .toList();
        
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

        // MOCK: Always return success with random high confidence
        // In production, this would call AWS Rekognition or similar
        double confidence = 95.0 + (new Random().nextDouble() * 4.9);
        boolean isMatch = true;

        return ResponseEntity.ok(Map.of(
            "match", isMatch,
            "confidence", confidence,
            "verified", isMatch && confidence > 85.0
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
                "radius_meters", o.getRadiusMeters()
        )).toList());
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
        response.put("face_match_score", a.getFaceMatchScore());
        response.put("verification_status", a.getVerificationStatus());
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

    private LocationValidation validateLocation(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return new LocationValidation(false, false, -1, "Unknown");
        }

        List<OfficeLocation> offices = officeLocationRepository.findByIsActiveTrue();
        if (offices.isEmpty()) {
            return new LocationValidation(true, true, 0, "No office configured");
        }

        // Find the nearest office
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
            return new LocationValidation(true, false, -1, "Unknown");
        }

        boolean withinRadius = minDistance <= nearest.getRadiusMeters();
        return new LocationValidation(true, withinRadius, minDistance, nearest.getName());
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth's radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    // ==================== REQUEST/RESPONSE RECORDS ====================

    public record CheckInRequest(
            Double latitude,
            Double longitude,
            Double accuracy,
            String address,
            String photoUrl,
            String deviceInfo,
            String note
    ) {}

    public record CheckOutRequest(String note) {}

    public record VerificationResult(
            boolean verified,
            Double matchScore,
            String note
    ) {}

    private record LocationValidation(
            boolean isValid,
            boolean withinRadius,
            double distanceMeters,
            String officeName
    ) {}
}
