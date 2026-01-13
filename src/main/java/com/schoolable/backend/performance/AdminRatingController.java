package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.hr.TeamLeadAppointment;
import com.schoolable.backend.hr.TeamLeadRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Admin Rating Controller
 * Allows Super Admin to rate Team Leads weekly.
 */
@RestController
@RequestMapping("/api/admin/ratings")
@Tag(name = "Admin - Team Lead Ratings")
public class AdminRatingController {

    @Autowired
    private AdminTeamLeadRatingRepository ratingRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private TeamLeadRepository teamLeadRepository;

    @Autowired
    private AuraTrendAlertRepository alertRepository;

    // ==================== GET TEAM LEADS ====================

    @Operation(summary = "Get all team leads for rating")
    @GetMapping("/team-leads")
    public ResponseEntity<?> getTeamLeadsForRating(Authentication auth) {
        Profile admin = getAdminProfile(auth);
        if (!isSuperAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Map<UUID, Profile> teamLeadIndex = new LinkedHashMap<>();
        List<Profile> flaggedLeads = profileRepository.findByIsTeamLeadTrue();
        for (Profile lead : flaggedLeads) {
            teamLeadIndex.put(lead.getId(), lead);
        }
        List<TeamLeadAppointment> appointments = teamLeadRepository.findActiveTeamLeads();
        for (TeamLeadAppointment appointment : appointments) {
            profileRepository.findById(appointment.getEmployeeId())
                .ifPresent(profile -> teamLeadIndex.putIfAbsent(profile.getId(), profile));
        }
        List<Profile> teamLeads = new ArrayList<>(teamLeadIndex.values());
        
        LocalDate today = LocalDate.now();
        int weekNumber = today.get(WeekFields.ISO.weekOfYear());
        int year = today.getYear();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Profile tl : teamLeads) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", tl.getId());
            data.put("name", tl.getFullName());
            data.put("email", tl.getEmail());
            data.put("department", tl.getDepartment());
            data.put("avatarUrl", tl.getAvatarUrl());
            
            // Check if rated this week
            boolean ratedThisWeek = ratingRepository.existsByTeamLeadIdAndWeekNumberAndYear(
                tl.getId(), weekNumber, year);
            data.put("ratedThisWeek", ratedThisWeek);
            
            // Get team size
            long teamSize = profileRepository.countByTeamLeadId(tl.getId());
            data.put("teamSize", teamSize);
            
            // Get latest rating
            Optional<AdminTeamLeadRating> latestRating = ratingRepository.findLatestByTeamLeadId(tl.getId());
            if (latestRating.isPresent()) {
                data.put("lastRatingAvg", latestRating.get().getAverageScore());
                data.put("lastRatingWeek", latestRating.get().getWeekNumber());
            }
            
            result.add(data);
        }

        return ResponseEntity.ok(Map.of(
            "teamLeads", result,
            "currentWeek", weekNumber,
            "currentYear", year,
            "totalTeamLeads", teamLeads.size()
        ));
    }

    // ==================== RECENT RATINGS ====================

    @Operation(summary = "Get recent team lead ratings")
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentRatings(Authentication auth) {
        Profile admin = getAdminProfile(auth);
        if (!isSuperAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can access this"));
        }

        List<AdminTeamLeadRating> ratings = ratingRepository.findTop10ByOrderByCreatedAtDesc();
        Set<UUID> teamLeadIds = new HashSet<>();
        for (AdminTeamLeadRating rating : ratings) {
            if (rating.getTeamLeadId() != null) {
                teamLeadIds.add(rating.getTeamLeadId());
            }
        }

        Map<UUID, Profile> teamLeadMap = profileRepository.findAllById(teamLeadIds)
            .stream()
            .collect(HashMap::new, (map, profile) -> map.put(profile.getId(), profile), HashMap::putAll);

        List<Map<String, Object>> result = new ArrayList<>();
        for (AdminTeamLeadRating rating : ratings) {
            Profile lead = teamLeadMap.get(rating.getTeamLeadId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", rating.getId());
            item.put("teamLeadId", rating.getTeamLeadId());
            item.put("teamLeadName", lead != null ? lead.getFullName() : "Unknown");
            item.put("department", lead != null ? lead.getDepartment() : null);
            item.put("averageScore", rating.getAverageScore());
            item.put("weekNumber", rating.getWeekNumber());
            item.put("year", rating.getYear());
            item.put("createdAt", rating.getCreatedAt());
            result.add(item);
        }

        return ResponseEntity.ok(Map.of("ratings", result));
    }

    // ==================== SUBMIT RATING ====================

    @Operation(summary = "Submit rating for a team lead")
    @PostMapping("/team-leads/{teamLeadId}")
    public ResponseEntity<?> submitRating(
            Authentication auth,
            @PathVariable UUID teamLeadId,
            @RequestBody RatingRequest request
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isSuperAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Profile teamLead = profileRepository.findById(teamLeadId).orElse(null);
        if (teamLead == null || !teamLead.getIsTeamLead()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid team lead ID"));
        }

        LocalDate today = LocalDate.now();
        int weekNumber = today.get(WeekFields.ISO.weekOfYear());
        int year = today.getYear();
        LocalDate weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        // Check if already rated this week (upsert)
        AdminTeamLeadRating rating = ratingRepository
            .findByTeamLeadIdAndWeekNumberAndYear(teamLeadId, weekNumber, year)
            .orElse(new AdminTeamLeadRating());

        rating.setTeamLeadId(teamLeadId);
        rating.setRatedById(admin.getId());
        rating.setWeekNumber(weekNumber);
        rating.setYear(year);
        rating.setWeekStartDate(weekStart);
        rating.setWeekEndDate(weekEnd);

        // Set scores
        rating.setLeadershipScore(request.leadershipScore);
        rating.setTeamManagementScore(request.teamManagementScore);
        rating.setCommunicationScore(request.communicationScore);
        rating.setResultsDeliveryScore(request.resultsDeliveryScore);
        rating.setCultureChampionScore(request.cultureChampionScore);

        // Set notes
        rating.setLeadershipNotes(request.leadershipNotes);
        rating.setAreasOfStrength(request.areasOfStrength);
        rating.setAreasForImprovement(request.areasForImprovement);

        ratingRepository.save(rating);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Rating submitted successfully",
            "rating", Map.of(
                "id", rating.getId(),
                "teamLeadId", teamLeadId,
                "weekNumber", weekNumber,
                "averageScore", rating.getAverageScore()
            )
        ));
    }

    // ==================== GET RATING HISTORY ====================

    @Operation(summary = "Get rating history for a team lead")
    @GetMapping("/team-leads/{teamLeadId}/history")
    public ResponseEntity<?> getRatingHistory(
            Authentication auth,
            @PathVariable UUID teamLeadId
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isSuperAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Profile teamLead = profileRepository.findById(teamLeadId).orElse(null);
        if (teamLead == null) {
            return ResponseEntity.notFound().build();
        }

        // Get current quarter weeks
        LocalDate today = LocalDate.now();
        int currentQuarter = (today.getMonthValue() - 1) / 3 + 1;
        int startWeek = (currentQuarter - 1) * 13 + 1;
        int endWeek = currentQuarter * 13;

        List<AdminTeamLeadRating> ratings = ratingRepository.findByTeamLeadAndQuarter(
            teamLeadId, today.getYear(), startWeek, endWeek);

        List<Map<String, Object>> ratingData = new ArrayList<>();
        for (AdminTeamLeadRating r : ratings) {
            Map<String, Object> rating = new HashMap<>();
            rating.put("weekNumber", r.getWeekNumber());
            rating.put("weekStart", r.getWeekStartDate().toString());
            rating.put("weekEnd", r.getWeekEndDate().toString());
            rating.put("leadershipScore", r.getLeadershipScore() != null ? r.getLeadershipScore() : 0);
            rating.put("teamManagementScore", r.getTeamManagementScore() != null ? r.getTeamManagementScore() : 0);
            rating.put("communicationScore", r.getCommunicationScore() != null ? r.getCommunicationScore() : 0);
            rating.put("resultsDeliveryScore", r.getResultsDeliveryScore() != null ? r.getResultsDeliveryScore() : 0);
            rating.put("cultureChampionScore", r.getCultureChampionScore() != null ? r.getCultureChampionScore() : 0);
            rating.put("averageScore", r.getAverageScore());
            rating.put("leadershipNotes", r.getLeadershipNotes());
            rating.put("areasOfStrength", r.getAreasOfStrength());
            rating.put("areasForImprovement", r.getAreasForImprovement());
            ratingData.add(rating);
        }

        return ResponseEntity.ok(Map.of(
            "teamLead", Map.of(
                "id", teamLead.getId(),
                "name", teamLead.getFullName(),
                "department", teamLead.getDepartment()
            ),
            "ratings", ratingData,
            "quarter", "Q" + currentQuarter,
            "year", today.getYear()
        ));
    }

    // ==================== GET AURA TREND ALERTS ====================

    @Operation(summary = "Get Aura trend alerts")
    @GetMapping("/alerts")
    public ResponseEntity<?> getAuraAlerts(Authentication auth) {
        Profile admin = getAdminProfile(auth);
        if (!isSuperAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        // Get unacknowledged alerts
        List<AuraTrendAlert> alerts = alertRepository.findByIsAcknowledgedFalseOrderByCreatedAtDesc();

        List<Map<String, Object>> alertData = new ArrayList<>();
        for (AuraTrendAlert alert : alerts) {
            Profile employee = profileRepository.findById(alert.getEmployeeId()).orElse(null);
            
            Map<String, Object> data = new HashMap<>();
            data.put("id", alert.getId());
            data.put("employeeId", alert.getEmployeeId());
            data.put("employeeName", employee != null ? employee.getFullName() : "Unknown");
            data.put("department", employee != null ? employee.getDepartment() : null);
            data.put("alertType", alert.getAlertType());
            data.put("previousScore", alert.getPreviousScore());
            data.put("currentScore", alert.getCurrentScore());
            data.put("changePercentage", alert.getChangePercentage());
            data.put("message", alert.getAlertMessage());
            data.put("weeksTrending", alert.getWeeksTrending());
            data.put("createdAt", alert.getCreatedAt());
            data.put("isRead", alert.getIsRead());
            
            alertData.add(data);
        }

        // Group by type
        long dropCount = alerts.stream().filter(a -> "SCORE_DROP".equals(a.getAlertType())).count();
        long declineCount = alerts.stream().filter(a -> "CONSISTENT_DECLINE".equals(a.getAlertType())).count();
        long improvementCount = alerts.stream().filter(a -> "CONSISTENT_IMPROVEMENT".equals(a.getAlertType())).count();

        return ResponseEntity.ok(Map.of(
            "alerts", alertData,
            "summary", Map.of(
                "total", alerts.size(),
                "scoreDrops", dropCount,
                "consistentDeclines", declineCount,
                "improvements", improvementCount
            )
        ));
    }

    // ==================== ACKNOWLEDGE ALERT ====================

    @Operation(summary = "Acknowledge an Aura alert")
    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledgeAlert(
            Authentication auth,
            @PathVariable Long alertId
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isSuperAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        AuraTrendAlert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null) {
            return ResponseEntity.notFound().build();
        }

        alert.setIsAcknowledged(true);
        alert.setAcknowledgedBy(admin.getId());
        alert.setAcknowledgedAt(java.time.OffsetDateTime.now());
        alertRepository.save(alert);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Alert acknowledged"
        ));
    }

    // ==================== HELPER METHODS ====================

    private Profile getAdminProfile(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        if (auth.getPrincipal() instanceof UUID uuid) {
            return profileRepository.findById(uuid).orElse(null);
        }
        String principal = auth.getPrincipal().toString();
        try {
            UUID userId = UUID.fromString(principal);
            return profileRepository.findById(userId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return profileRepository.findByEmail(principal).orElse(null);
        }
    }

    private boolean isSuperAdmin(Authentication auth, Profile profile) {
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))) {
            return true;
        }
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return true;
        }
        if (profile == null || profile.getRole() == null) return false;
        String role = profile.getRole().toLowerCase(Locale.ROOT);
        return role.equals("super_admin") || role.equals("superadmin") || role.equals("admin");
    }

    // ==================== REQUEST CLASS ====================

    public static class RatingRequest {
        public Integer leadershipScore;
        public Integer teamManagementScore;
        public Integer communicationScore;
        public Integer resultsDeliveryScore;
        public Integer cultureChampionScore;
        public String leadershipNotes;
        public String areasOfStrength;
        public String areasForImprovement;
    }
}
