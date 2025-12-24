package com.schoolable.backend.announcement;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/announcements")
@Tag(name = "Announcements")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final ProfileRepository profileRepository;

    public AnnouncementController(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository announcementReadRepository,
            ProfileRepository profileRepository) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.profileRepository = profileRepository;
    }

    @Operation(summary = "Get all active announcements for current user")
    @GetMapping
    public ResponseEntity<?> getAnnouncements(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Get user's department for audience filtering
        var profileOpt = profileRepository.findById(userId);
        String userDepartment = profileOpt.map(Profile::getDepartment).orElse(null);

        // Get IDs of announcements the user has read
        Set<UUID> readIds = announcementReadRepository.findByUserId(userId)
                .stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toSet());

        // Get all active announcements
        List<Announcement> announcements = announcementRepository.findActiveAnnouncements();

        // Filter by audience and map to response
        List<Map<String, Object>> result = announcements.stream()
                .filter(a -> {
                    String audience = a.getAudience();
                    if (audience == null || "All Staff".equalsIgnoreCase(audience)) {
                        return true;
                    }
                    return userDepartment != null && audience.equalsIgnoreCase(userDepartment);
                })
                .map(a -> buildAnnouncementResponse(a, readIds.contains(a.getId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get unread announcements for current user")
    @GetMapping("/unread")
    public ResponseEntity<?> getUnreadAnnouncements(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Get user's department for audience filtering
        var profileOpt = profileRepository.findById(userId);
        String userDepartment = profileOpt.map(Profile::getDepartment).orElse(null);

        // Get IDs of announcements the user has read
        Set<UUID> readIds = announcementReadRepository.findByUserId(userId)
                .stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toSet());

        // Get all active announcements, filter out read ones
        List<Announcement> announcements = announcementRepository.findActiveAnnouncements();

        List<Map<String, Object>> result = announcements.stream()
                .filter(a -> !readIds.contains(a.getId())) // Exclude read
                .filter(a -> {
                    String audience = a.getAudience();
                    if (audience == null || "All Staff".equalsIgnoreCase(audience)) {
                        return true;
                    }
                    return userDepartment != null && audience.equalsIgnoreCase(userDepartment);
                })
                .map(a -> buildAnnouncementResponse(a, false))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get a single announcement")
    @GetMapping("/{id}")
    public ResponseEntity<?> getAnnouncement(@PathVariable UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var announcementOpt = announcementRepository.findById(id);
        if (announcementOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        boolean isRead = announcementReadRepository.existsByUserIdAndAnnouncementId(userId, id);
        return ResponseEntity.ok(buildAnnouncementResponse(announcementOpt.get(), isRead));
    }

    @Operation(summary = "Create a new announcement (admin only)")
    @PostMapping
    public ResponseEntity<?> createAnnouncement(@RequestBody CreateAnnouncementRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Check if user is admin
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !"admin".equalsIgnoreCase(profileOpt.get().getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can create announcements"));
        }

        Announcement announcement = new Announcement();
        announcement.setId(UUID.randomUUID());
        announcement.setTitle(req.title());
        announcement.setContent(req.content());
        announcement.setAudience(req.audience() != null ? req.audience() : "All Staff");
        announcement.setPinned(req.pinned() != null ? req.pinned() : false);
        announcement.setStatus(req.status() != null ? req.status() : "Published");
        announcement.setScheduledAt(req.scheduledAt() != null ? OffsetDateTime.parse(req.scheduledAt()) : null);
        announcement.setAuthorId(userId);
        announcement.setCreatedAt(OffsetDateTime.now());

        announcementRepository.save(announcement);

        return ResponseEntity.ok(buildAnnouncementResponse(announcement, false));
    }

    @Operation(summary = "Update an announcement (admin only)")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAnnouncement(@PathVariable UUID id, @RequestBody CreateAnnouncementRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Check if user is admin
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !"admin".equalsIgnoreCase(profileOpt.get().getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can update announcements"));
        }

        var announcementOpt = announcementRepository.findById(id);
        if (announcementOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        Announcement announcement = announcementOpt.get();
        if (req.title() != null) announcement.setTitle(req.title());
        if (req.content() != null) announcement.setContent(req.content());
        if (req.audience() != null) announcement.setAudience(req.audience());
        if (req.pinned() != null) announcement.setPinned(req.pinned());
        if (req.status() != null) announcement.setStatus(req.status());
        if (req.scheduledAt() != null) announcement.setScheduledAt(OffsetDateTime.parse(req.scheduledAt()));

        announcementRepository.save(announcement);

        return ResponseEntity.ok(buildAnnouncementResponse(announcement, false));
    }

    @Operation(summary = "Delete an announcement (admin only)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Check if user is admin
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !"admin".equalsIgnoreCase(profileOpt.get().getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can delete announcements"));
        }

        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        announcementRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Mark an announcement as read")
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        // Upsert the read record
        AnnouncementRead read = new AnnouncementRead(userId, id);
        announcementReadRepository.save(read);

        return ResponseEntity.ok(Map.of("success", true));
    }

    private Map<String, Object> buildAnnouncementResponse(Announcement a, boolean isRead) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", a.getId());
        response.put("title", a.getTitle());
        response.put("content", a.getContent());
        response.put("audience", a.getAudience());
        response.put("pinned", a.getPinned());
        response.put("status", a.getStatus());
        response.put("scheduled_at", a.getScheduledAt());
        response.put("author_id", a.getAuthorId());
        response.put("created_at", a.getCreatedAt());
        response.put("is_read", isRead);
        return response;
    }

    public record CreateAnnouncementRequest(
            String title,
            String content,
            String audience,
            Boolean pinned,
            String status,
            String scheduledAt
    ) {}
}
