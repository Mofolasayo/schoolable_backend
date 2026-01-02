package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for weekly peer helpfulness ratings
 * Employees rate how helpful their colleagues were to them each week
 */
@RestController
@RequestMapping("/api/peer-helpfulness")
@CrossOrigin(origins = "*")
public class PeerHelpfulnessController {

    @Autowired
    private PeerHelpfulnessRepository helpfulnessRepository;

    @Autowired
    private ProfileRepository profileRepository;

    // ==================== SUBMIT RATINGS ====================

    public record PeerRatingRequest(
        List<IndividualRating> ratings
    ) {}

    public record IndividualRating(
        UUID userId,
        Integer rating, // 1-5
        String comment
    ) {}

    /**
     * POST /api/peer-helpfulness/submit
     * Submit weekly helpfulness ratings for colleagues
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitRatings(
            Authentication auth,
            @RequestBody PeerRatingRequest request,
            @RequestParam(required = false) Integer weekNumber,
            @RequestParam(required = false) Integer year) {

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID raterId = (UUID) auth.getPrincipal();
        Profile raterProfile = profileRepository.findById(raterId).orElse(null);
        
        if (raterProfile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        // Default to current week
        LocalDate now = LocalDate.now();
        if (weekNumber == null) {
            weekNumber = now.get(WeekFields.ISO.weekOfYear());
        }
        if (year == null) {
            year = now.getYear();
        }

        List<PeerHelpfulnessRating> savedRatings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (IndividualRating ir : request.ratings()) {
            if (ir.userId().equals(raterId)) {
                errors.add("Cannot rate yourself");
                continue;
            }

            if (ir.rating() < 1 || ir.rating() > 5) {
                errors.add("Rating must be between 1 and 5 for user " + ir.userId());
                continue;
            }

            // Check if already rated this person this week
            Optional<PeerHelpfulnessRating> existing = helpfulnessRepository
                .findByRaterIdAndRatedUserIdAndWeekNumberAndYear(raterId, ir.userId(), weekNumber, year);

            PeerHelpfulnessRating rating;
            if (existing.isPresent()) {
                // Update existing rating
                rating = existing.get();
                rating.setRating(ir.rating());
                rating.setComment(ir.comment());
            } else {
                // Create new rating
                rating = new PeerHelpfulnessRating();
                rating.setRaterId(raterId);
                rating.setRatedUserId(ir.userId());
                rating.setWeekNumber(weekNumber);
                rating.setYear(year);
                rating.setRating(ir.rating());
                rating.setComment(ir.comment());
                rating.setOrganization(raterProfile.getDepartment()); // Use department as org
            }

            savedRatings.add(helpfulnessRepository.save(rating));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Ratings submitted successfully",
            "ratingsCount", savedRatings.size(),
            "weekNumber", weekNumber,
            "year", year,
            "errors", errors
        ));
    }

    // ==================== GET COLLEAGUES TO RATE ====================

    /**
     * GET /api/peer-helpfulness/colleagues
     * Get list of colleagues to rate this week
     */
    @GetMapping("/colleagues")
    public ResponseEntity<?> getColleaguesToRate(
            Authentication auth,
            @RequestParam(required = false) Integer weekNumber,
            @RequestParam(required = false) Integer year) {

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        LocalDate now = LocalDate.now();
        if (weekNumber == null) {
            weekNumber = now.get(WeekFields.ISO.weekOfYear());
        }
        if (year == null) {
            year = now.getYear();
        }

        // Get colleagues from same department
        List<Profile> colleagues = profileRepository.findByDepartment(profile.getDepartment())
            .stream()
            .filter(p -> !p.getId().equals(userId))
            .filter(p -> "approved".equalsIgnoreCase(p.getStatus()))
            .toList();

        // Get existing ratings for this week
        List<PeerHelpfulnessRating> existingRatings = 
            helpfulnessRepository.findByRaterIdAndWeekNumberAndYear(userId, weekNumber, year);
        
        Set<UUID> alreadyRated = existingRatings.stream()
            .map(PeerHelpfulnessRating::getRatedUserId)
            .collect(Collectors.toSet());

        List<Map<String, Object>> colleagueList = colleagues.stream()
            .map(c -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", c.getId());
                // Parse fullName into firstName/lastName for frontend compatibility
                String fullName = c.getFullName() != null ? c.getFullName() : "";
                String[] nameParts = fullName.split(" ", 2);
                data.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
                data.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
                data.put("fullName", fullName);
                data.put("email", c.getEmail());
                data.put("department", c.getDepartment());
                data.put("jobTitle", c.getJobTitle());
                data.put("avatarUrl", c.getAvatarUrl());
                data.put("alreadyRated", alreadyRated.contains(c.getId()));
                
                // Get existing rating if any
                existingRatings.stream()
                    .filter(r -> r.getRatedUserId().equals(c.getId()))
                    .findFirst()
                    .ifPresent(r -> {
                        data.put("currentRating", r.getRating());
                        data.put("currentComment", r.getComment());
                    });
                    
                return data;
            })
            .toList();

        return ResponseEntity.ok(Map.of(
            "colleagues", colleagueList,
            "weekNumber", weekNumber,
            "year", year,
            "totalColleagues", colleagues.size(),
            "alreadyRatedCount", alreadyRated.size(),
            "pendingRatings", colleagues.size() - alreadyRated.size()
        ));
    }

    // ==================== GET MY RATINGS RECEIVED ====================

    /**
     * GET /api/peer-helpfulness/received
     * Get ratings received from colleagues
     */
    @GetMapping("/received")
    public ResponseEntity<?> getReceivedRatings(
            Authentication auth,
            @RequestParam(required = false) Integer weekNumber,
            @RequestParam(required = false) Integer year) {

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();

        LocalDate now = LocalDate.now();
        if (weekNumber == null) {
            weekNumber = now.get(WeekFields.ISO.weekOfYear());
        }
        if (year == null) {
            year = now.getYear();
        }

        List<PeerHelpfulnessRating> ratings = 
            helpfulnessRepository.findByRatedUserIdAndWeekNumberAndYear(userId, weekNumber, year);

        Double averageRating = ratings.isEmpty() ? null :
            ratings.stream().mapToInt(PeerHelpfulnessRating::getRating).average().orElse(0);

        return ResponseEntity.ok(Map.of(
            "weekNumber", weekNumber,
            "year", year,
            "totalRatings", ratings.size(),
            "averageRating", averageRating != null ? Math.round(averageRating * 10) / 10.0 : null
        ));
    }

    // ==================== CHECK RATING STATUS ====================

    /**
     * GET /api/peer-helpfulness/status
     * Check if user has completed their weekly ratings
     */
    @GetMapping("/status")
    public ResponseEntity<?> getRatingStatus(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);

        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Profile not found"));
        }

        LocalDate now = LocalDate.now();
        int weekNumber = now.get(WeekFields.ISO.weekOfYear());
        int year = now.getYear();

        // Count colleagues from same department
        long colleagueCount = profileRepository.findByDepartment(profile.getDepartment())
            .stream()
            .filter(p -> !p.getId().equals(userId))
            .filter(p -> "approved".equalsIgnoreCase(p.getStatus()))
            .count();

        // Count ratings given this week
        long ratingsGiven = helpfulnessRepository.countByRaterIdAndWeekNumberAndYear(userId, weekNumber, year);

        boolean isComplete = ratingsGiven >= colleagueCount;
        long pendingRatings = Math.max(0, colleagueCount - ratingsGiven);

        return ResponseEntity.ok(Map.of(
            "weekNumber", weekNumber,
            "year", year,
            "totalColleagues", colleagueCount,
            "ratingsGiven", ratingsGiven,
            "pendingRatings", pendingRatings,
            "isComplete", isComplete,
            "promptMessage", isComplete ? null : 
                "Please rate how helpful your " + pendingRatings + " remaining colleagues were this week"
        ));
    }
}
