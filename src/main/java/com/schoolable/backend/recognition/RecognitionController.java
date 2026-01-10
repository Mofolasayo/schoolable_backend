package com.schoolable.backend.recognition;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recognition Controller
 * Handles kudos/recognition between employees.
 */
@RestController
@RequestMapping("/recognitions")
@Tag(name = "Recognition", description = "Kudos and recognition system")
public class RecognitionController {

    @Autowired
    private RecognitionRepository recognitionRepository;

    @Autowired
    private ProfileRepository profileRepository;

    private static final List<String> VALID_CATEGORIES = List.of(
        "teamwork", "innovation", "leadership", "customer-focus", "above-and-beyond", "helpfulness"
    );

    private static final Map<String, Integer> CATEGORY_POINTS = Map.of(
        "teamwork", 10,
        "innovation", 15,
        "leadership", 15,
        "customer-focus", 10,
        "above-and-beyond", 20,
        "helpfulness", 10
    );

    /**
     * Give recognition to a colleague
     */
    @PostMapping
    @Operation(summary = "Give recognition", description = "Send kudos to a colleague")
    public ResponseEntity<?> giveRecognition(@RequestBody RecognitionRequest req, Authentication auth) {
        UUID fromUserId = UUID.fromString(auth.getName());
        
        // Validate
        if (req.toUserId == null || req.message == null || req.category == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "toUserId, message, and category are required"));
        }

        if (!VALID_CATEGORIES.contains(req.category.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid category",
                "validCategories", VALID_CATEGORIES
            ));
        }

        // Can't recognize yourself
        if (fromUserId.equals(req.toUserId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot give recognition to yourself"));
        }

        // Verify recipient exists
        Profile recipient = profileRepository.findById(req.toUserId).orElse(null);
        if (recipient == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Recipient not found"));
        }

        Profile sender = profileRepository.findById(fromUserId).orElse(null);

        // Create recognition
        Recognition recognition = new Recognition();
        recognition.setFromUserId(fromUserId);
        recognition.setToUserId(req.toUserId);
        recognition.setMessage(req.message);
        recognition.setCategory(req.category.toLowerCase());
        recognition.setIsPublic(req.isPublic != null ? req.isPublic : true);
        recognition.setPoints(CATEGORY_POINTS.getOrDefault(req.category.toLowerCase(), 10));
        recognition.setTaskId(req.taskId);
        recognition.setAchievementType(req.achievementType);
        
        if (sender != null) {
            recognition.setOrganization(sender.getDepartment());
            recognition.setDepartment(sender.getDepartment());
        }

        recognitionRepository.save(recognition);

        // TODO: Trigger notification to recipient

        return ResponseEntity.ok(buildRecognitionResponse(recognition, sender, recipient));
    }

    /**
     * Get recognition feed (public recognitions)
     */
    @GetMapping("/feed")
    @Operation(summary = "Get recognition feed", description = "Get public recognitions feed")
    public ResponseEntity<?> getRecognitionFeed(
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        
        List<Recognition> recognitions = recognitionRepository
            .findByIsPublicTrueOrderByCreatedAtDesc(PageRequest.of(0, limit));

        List<Map<String, Object>> feed = recognitions.stream()
            .map(r -> {
                Profile sender = profileRepository.findById(r.getFromUserId()).orElse(null);
                Profile recipient = profileRepository.findById(r.getToUserId()).orElse(null);
                return buildRecognitionResponse(r, sender, recipient);
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "recognitions", feed,
            "count", feed.size()
        ));
    }

    /**
     * Get my received recognitions
     */
    @GetMapping("/received")
    @Operation(summary = "Get received recognitions")
    public ResponseEntity<?> getReceivedRecognitions(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        
        List<Recognition> recognitions = recognitionRepository.findByToUserIdOrderByCreatedAtDesc(userId);
        int totalPoints = recognitionRepository.getTotalPointsReceived(userId);

        List<Map<String, Object>> items = recognitions.stream()
            .map(r -> {
                Profile sender = profileRepository.findById(r.getFromUserId()).orElse(null);
                return buildRecognitionResponse(r, sender, null);
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "recognitions", items,
            "count", items.size(),
            "totalPoints", totalPoints
        ));
    }

    /**
     * Get recognitions I've given
     */
    @GetMapping("/given")
    @Operation(summary = "Get given recognitions")
    public ResponseEntity<?> getGivenRecognitions(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        
        List<Recognition> recognitions = recognitionRepository.findByFromUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> items = recognitions.stream()
            .map(r -> {
                Profile recipient = profileRepository.findById(r.getToUserId()).orElse(null);
                return buildRecognitionResponse(r, null, recipient);
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "recognitions", items,
            "count", items.size()
        ));
    }

    /**
     * Get recognition leaderboard
     */
    @GetMapping("/leaderboard")
    @Operation(summary = "Get recognition leaderboard")
    public ResponseEntity<?> getLeaderboard(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> topRecognized = recognitionRepository.getTopRecognized(since, PageRequest.of(0, limit));

        List<Map<String, Object>> leaderboard = topRecognized.stream()
            .map(row -> {
                UUID userId = (UUID) row[0];
                Long count = (Long) row[1];
                Profile profile = profileRepository.findById(userId).orElse(null);
                int points = recognitionRepository.getTotalPointsReceived(userId);

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("userId", userId.toString());
                entry.put("name", profile != null ? profile.getFullName() : "Unknown");
                entry.put("department", profile != null ? profile.getDepartment() : "");
                entry.put("avatarUrl", profile != null && profile.getAvatarUrl() != null ? profile.getAvatarUrl() : "");
                entry.put("recognitionCount", count);
                entry.put("totalPoints", points);
                return entry;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "leaderboard", leaderboard,
            "period", days + " days",
            "categories", VALID_CATEGORIES
        ));
    }

    /**
     * Get available categories
     */
    @GetMapping("/categories")
    @Operation(summary = "Get recognition categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(Map.of(
            "categories", VALID_CATEGORIES,
            "points", CATEGORY_POINTS
        ));
    }

    private Map<String, Object> buildRecognitionResponse(Recognition r, Profile sender, Profile recipient) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", r.getId().toString());
        response.put("message", r.getMessage());
        response.put("category", r.getCategory());
        response.put("points", r.getPoints());
        response.put("isPublic", r.getIsPublic());
        response.put("createdAt", r.getCreatedAt().toString());

        if (sender != null) {
            response.put("from", Map.of(
                "id", sender.getId().toString(),
                "name", sender.getFullName(),
                "avatarUrl", sender.getAvatarUrl() != null ? sender.getAvatarUrl() : ""
            ));
        }

        if (recipient != null) {
            response.put("to", Map.of(
                "id", recipient.getId().toString(),
                "name", recipient.getFullName(),
                "avatarUrl", recipient.getAvatarUrl() != null ? recipient.getAvatarUrl() : ""
            ));
        }

        if (r.getTaskId() != null) {
            response.put("taskId", r.getTaskId());
        }
        if (r.getAchievementType() != null) {
            response.put("achievementType", r.getAchievementType());
        }

        return response;
    }

    // Request DTO
    public static class RecognitionRequest {
        public UUID toUserId;
        public String message;
        public String category;
        public Boolean isPublic;
        public Long taskId;
        public String achievementType;
    }
}
