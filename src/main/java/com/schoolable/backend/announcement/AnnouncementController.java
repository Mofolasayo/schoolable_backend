package com.schoolable.backend.announcement;

import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.websocket.WebSocketMessageController;
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
    private final NotificationService notificationService;
    private final WebSocketMessageController webSocketController;

    public AnnouncementController(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository announcementReadRepository,
            ProfileRepository profileRepository,
            NotificationService notificationService,
            WebSocketMessageController webSocketController) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.profileRepository = profileRepository;
        this.notificationService = notificationService;
        this.webSocketController = webSocketController;
    }

    @Operation(summary = "Get all active announcements for current user")
    @GetMapping
    public ResponseEntity<?> getAnnouncements(Authentication auth) {
        UUID userId = getUserId(auth);

        // Get user's department for audience filtering
        var profileOpt = profileRepository.findById(userId);
        String userDepartment = profileOpt.map(Profile::getDepartment).orElse(null);
        boolean isTeamLead = profileOpt.map(this::isTeamLeadProfile).orElse(false);

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
                    if (isTeamLeadAudience(audience)) {
                        return isTeamLead;
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
        UUID userId = getUserId(auth);

        // Get user's department for audience filtering
        var profileOpt = profileRepository.findById(userId);
        String userDepartment = profileOpt.map(Profile::getDepartment).orElse(null);
        boolean isTeamLead = profileOpt.map(this::isTeamLeadProfile).orElse(false);

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
                    if (isTeamLeadAudience(audience)) {
                        return isTeamLead;
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
        UUID userId = getUserId(auth);

        var announcementOpt = announcementRepository.findById(id);
        if (announcementOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        boolean isRead = announcementReadRepository.existsByUserIdAndAnnouncementId(userId, id);
        return ResponseEntity.ok(buildAnnouncementResponse(announcementOpt.get(), isRead));
    }

    @Operation(summary = "Get list of users who read an announcement (admin or team lead)")
    @GetMapping("/{id}/reads")
    public ResponseEntity<?> getAnnouncementReads(@PathVariable UUID id, Authentication auth) {
        UUID userId = getUserId(auth);

        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Profile not found"));
        }

        Profile profile = profileOpt.get();
        boolean isAdmin = isAdminProfile(profile);
        boolean isTeamLead = isTeamLeadProfile(profile);
        if (!isAdmin && !isTeamLead) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins and team leads can view readers"));
        }

        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        List<AnnouncementRead> reads = announcementReadRepository.findByAnnouncementId(id);
        if (reads.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<UUID> readerIds = reads.stream()
                .map(AnnouncementRead::getUserId)
                .distinct()
                .toList();

        Map<UUID, Profile> profilesById = profileRepository.findAllById(readerIds).stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));

        List<Map<String, Object>> result = reads.stream()
                .sorted(Comparator.comparing(AnnouncementRead::getReadAt).reversed())
                .map(read -> {
                    Map<String, Object> row = new HashMap<>();
                    Profile reader = profilesById.get(read.getUserId());
                    row.put("user_id", read.getUserId());
                    row.put("read_at", read.getReadAt());
                    if (reader != null) {
                        row.put("full_name", reader.getFullName());
                        row.put("email", reader.getEmail());
                        row.put("department", reader.getDepartment());
                        row.put("role", reader.getRole());
                        row.put("is_team_lead", reader.getIsTeamLead());
                    }
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Create a new announcement (admin or team lead)")
    @PostMapping
    public ResponseEntity<?> createAnnouncement(@RequestBody CreateAnnouncementRequest req, Authentication auth) {
        UUID userId = getUserId(auth);

        // Check if user is admin or team lead
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Profile not found"));
        }
        
        var profile = profileOpt.get();
        boolean isAdmin = isAdminProfile(profile);
        boolean isTeamLead = isTeamLeadProfile(profile);
        
        if (!isAdmin && !isTeamLead) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins and team leads can create announcements"));
        }
        
        // If team lead (not admin), force audience to their department
        String audience = req.audience();
        if (!isAdmin && isTeamLead) {
            // Team leads can only create announcements for their department
            audience = profile.getDepartment() != null ? profile.getDepartment() : "All Staff";
        } else if (audience == null) {
            audience = "All Staff";
        }

        Announcement announcement = new Announcement();
        announcement.setId(UUID.randomUUID());
        announcement.setTitle(req.title());
        announcement.setContent(req.content());
        announcement.setAudience(audience);
        announcement.setPinned(req.pinned() != null ? req.pinned() : false);
        announcement.setStatus(req.status() != null ? req.status() : "Published");
        announcement.setScheduledAt(req.scheduledAt() != null ? OffsetDateTime.parse(req.scheduledAt()) : null);
        announcement.setAuthorId(userId);
        announcement.setCreatedAt(OffsetDateTime.now());

        announcementRepository.save(announcement);

        if (isPublishedNow(announcement)) {
            List<UUID> recipients = resolveAudienceRecipients(audience);
            if (!recipients.isEmpty()) {
                String preview = buildPreview(announcement.getContent());
                Map<String, Object> data = new HashMap<>();
                data.put("announcementId", announcement.getId().toString());
                data.put("action", "open_announcement");
                notificationService.sendToUsers(
                    recipients,
                    "New Announcement",
                    announcement.getTitle() + (preview != null ? ": " + preview : ""),
                    NotificationService.TYPE_ANNOUNCEMENT,
                    announcement.getId().toString(),
                    data
                );
            }
        }

        webSocketController.broadcastAnnouncementUpdate(
            "created",
            announcement.getId(),
            buildAnnouncementResponse(announcement, false)
        );

        return ResponseEntity.ok(buildAnnouncementResponse(announcement, false));
    }

    @Operation(summary = "Update an announcement (admin only)")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAnnouncement(@PathVariable UUID id, @RequestBody CreateAnnouncementRequest req, Authentication auth) {
        UUID userId = getUserId(auth);

        // Check if user is admin
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !isAdminProfile(profileOpt.get())) {
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

        webSocketController.broadcastAnnouncementUpdate(
            "updated",
            announcement.getId(),
            buildAnnouncementResponse(announcement, false)
        );

        return ResponseEntity.ok(buildAnnouncementResponse(announcement, false));
    }

    @Operation(summary = "Delete an announcement (admin only)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable UUID id, Authentication auth) {
        UUID userId = getUserId(auth);

        // Check if user is admin
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !isAdminProfile(profileOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can delete announcements"));
        }

        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }

        announcementRepository.deleteById(id);

        webSocketController.broadcastAnnouncementUpdate(
            "deleted",
            id,
            Map.of("id", id.toString())
        );
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Mark an announcement as read")
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID id, Authentication auth) {
        UUID userId = getUserId(auth);

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

    private boolean isTeamLeadAudience(String audience) {
        return "Team Leads".equalsIgnoreCase(audience) || "Team Lead".equalsIgnoreCase(audience);
    }

    private boolean isPublishedNow(Announcement announcement) {
        if (announcement == null) {
            return false;
        }
        String status = announcement.getStatus();
        if (status == null) {
            return true;
        }
        boolean published = "published".equalsIgnoreCase(status);
        if (!published) {
            return false;
        }
        OffsetDateTime scheduledAt = announcement.getScheduledAt();
        return scheduledAt == null || !scheduledAt.isAfter(OffsetDateTime.now());
    }

    private List<UUID> resolveAudienceRecipients(String audience) {
        if (audience == null || "All Staff".equalsIgnoreCase(audience)) {
            return profileRepository.findByStatus("active").stream()
                .map(Profile::getId)
                .toList();
        }
        if (isTeamLeadAudience(audience)) {
            return profileRepository.findByIsTeamLeadTrue().stream()
                .filter(profile -> "active".equalsIgnoreCase(profile.getStatus()))
                .map(Profile::getId)
                .toList();
        }
        return profileRepository.findByDepartmentAndStatus(audience, "active").stream()
            .map(Profile::getId)
            .toList();
    }

    private String buildPreview(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= 80) {
            return trimmed;
        }
        return trimmed.substring(0, 77) + "...";
    }

    private boolean isAdminProfile(Profile profile) {
        if (profile == null) {
            return false;
        }
        String role = profile.getRole();
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("admin") || normalized.equals("super_admin") || normalized.equals("super admin");
    }

    private boolean isTeamLeadProfile(Profile profile) {
        if (profile == null) {
            return false;
        }
        if (Boolean.TRUE.equals(profile.getIsTeamLead())) {
            return true;
        }
        String role = profile.getRole();
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("team_lead") || normalized.equals("team lead") || normalized.contains("team lead");
    }

    private UUID getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        return UUID.fromString(principal.toString());
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
