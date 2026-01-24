package com.schoolable.backend.notifications;

import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.Locale;
import java.util.*;

/**
 * Smart Reminders Controller
 * Manages automated reminder configurations for the system.
 */
@RestController
@RequestMapping("/api/admin/smart-reminders")
@Tag(name = "Admin - Smart Reminders")
public class SmartRemindersController {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private SmartReminderRepository reminderRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SmartReminderAudienceService audienceService;

    // ==================== GET ALL REMINDERS ====================

    @Operation(summary = "Get all smart reminders")
    @GetMapping
    public ResponseEntity<?> getReminders(Authentication auth) {
        Profile admin = getAdminProfile(auth);
        if (!isAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<SmartReminder> reminders = reminderRepository.findAllByOrderByCreatedAtDesc();
        
        // Calculate summary stats
        int activeCount = (int) reminders.stream().filter(SmartReminder::isActive).count();
        int totalTriggers = reminders.stream().mapToInt(SmartReminder::getTriggerCount).sum();

        return ResponseEntity.ok(Map.of(
            "reminders", reminders,
            "summary", Map.of(
                "total", reminders.size(),
                "active", activeCount,
                "inactive", reminders.size() - activeCount,
                "totalTriggers", totalTriggers
            )
        ));
    }

    // ==================== CREATE REMINDER ====================

    @Operation(summary = "Create a new smart reminder")
    @PostMapping
    public ResponseEntity<?> createReminder(
            @RequestBody CreateReminderRequest req,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        SmartReminder reminder = new SmartReminder();
        reminder.setName(req.name);
        reminder.setDescription(req.description);
        reminder.setType(req.type);
        reminder.setScheduleTime(req.scheduleTime);
        reminder.setScheduleDays(String.join(",", req.scheduleDays != null ? req.scheduleDays : List.of()));
        reminder.setTimezone(req.timezone != null ? req.timezone : "Africa/Lagos");
        reminder.setTargetAudience(req.targetAudience);
        reminder.setMessage(req.message);
        reminder.setChannels("push");
        reminder.setActive(true);
        reminder.setTriggerCount(0);
        reminder.setCreatedAt(OffsetDateTime.now());
        reminder.setCreatedBy(admin.getId());

        reminder = reminderRepository.save(reminder);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Reminder created successfully",
            "reminder", buildReminderResponse(reminder)
        ));
    }

    // ==================== UPDATE REMINDER ====================

    @Operation(summary = "Update an existing reminder")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateReminder(
            @PathVariable Long id,
            @RequestBody CreateReminderRequest req,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        Optional<SmartReminder> optReminder = reminderRepository.findById(id);
        if (optReminder.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Reminder not found"));
        }

        SmartReminder reminder = optReminder.get();
        if (req.name != null) reminder.setName(req.name);
        if (req.description != null) reminder.setDescription(req.description);
        if (req.type != null) reminder.setType(req.type);
        if (req.scheduleTime != null) reminder.setScheduleTime(req.scheduleTime);
        if (req.scheduleDays != null) reminder.setScheduleDays(String.join(",", req.scheduleDays));
        if (req.timezone != null) reminder.setTimezone(req.timezone);
        if (req.targetAudience != null) reminder.setTargetAudience(req.targetAudience);
        if (req.message != null) reminder.setMessage(req.message);
        reminder.setChannels("push");

        reminder = reminderRepository.save(reminder);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Reminder updated successfully",
            "reminder", buildReminderResponse(reminder)
        ));
    }

    // ==================== TOGGLE REMINDER STATUS ====================

    @Operation(summary = "Toggle reminder active status")
    @PostMapping("/{id}/toggle")
    public ResponseEntity<?> toggleReminder(
            @PathVariable Long id,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        Optional<SmartReminder> optReminder = reminderRepository.findById(id);
        if (optReminder.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Reminder not found"));
        }

        SmartReminder reminder = optReminder.get();
        reminder.setActive(!reminder.isActive());
        reminder = reminderRepository.save(reminder);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", reminder.isActive() ? "Reminder activated" : "Reminder paused",
            "reminder", buildReminderResponse(reminder)
        ));
    }

    // ==================== DELETE REMINDER ====================

    @Operation(summary = "Delete a reminder")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReminder(
            @PathVariable Long id,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        if (!reminderRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Reminder not found"));
        }

        reminderRepository.deleteById(id);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Reminder deleted successfully"
        ));
    }

    // ==================== TRIGGER REMINDER MANUALLY ====================

    @Operation(summary = "Trigger a reminder manually")
    @PostMapping("/{id}/trigger")
    public ResponseEntity<?> triggerReminder(
            @PathVariable Long id,
            Authentication auth
    ) {
        Profile admin = getAdminProfile(auth);
        if (!isAdmin(auth, admin)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        Optional<SmartReminder> optReminder = reminderRepository.findById(id);
        if (optReminder.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Reminder not found"));
        }

        SmartReminder reminder = optReminder.get();
        
        // Get targeted users based on targetAudience
        List<UUID> targetUserIds = audienceService.resolveTargetUserIds(reminder.getTargetAudience());
        
        if (!targetUserIds.isEmpty()) {
            // Send notifications to all targeted users
            String title = "📢 " + reminder.getName();
            String body = reminder.getMessage();
            
            Map<String, Object> data = new HashMap<>();
            data.put("action", "open_announcement");
            data.put("reminderId", reminder.getId());
            data.put("type", "smart_reminder");
            
            for (UUID userId : targetUserIds) {
                notificationService.sendToUser(userId, title, body, "SMART_REMINDER", reminder.getId().toString(), data);
            }
        }
        
        // Update trigger count and last triggered
        reminder.setTriggerCount(reminder.getTriggerCount() + 1);
        reminder.setLastTriggered(OffsetDateTime.now());
        reminder = reminderRepository.save(reminder);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Reminder triggered and sent to " + targetUserIds.size() + " users",
            "usersNotified", targetUserIds.size(),
            "reminder", buildReminderResponse(reminder)
        ));
    }
    
    // ==================== HELPER METHODS ====================

    private Map<String, Object> buildReminderResponse(SmartReminder r) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", r.getId());
        response.put("name", r.getName());
        response.put("description", r.getDescription());
        response.put("type", r.getType());
        response.put("schedule", Map.of(
            "time", r.getScheduleTime(),
            "days", r.getScheduleDays() == null || r.getScheduleDays().isBlank()
                ? List.of()
                : Arrays.asList(r.getScheduleDays().split(",")),
            "timezone", r.getTimezone()
        ));
        response.put("targetAudience", r.getTargetAudience());
        response.put("message", r.getMessage());
        response.put("channels", r.getChannels() == null || r.getChannels().isBlank()
            ? List.of("push")
            : Arrays.asList(r.getChannels().split(",")));
        response.put("isActive", r.isActive());
        response.put("lastTriggered", r.getLastTriggered() != null ? r.getLastTriggered().toString() : null);
        response.put("triggerCount", r.getTriggerCount());
        response.put("createdAt", r.getCreatedAt().toString());
        return response;
    }

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
            return profileRepository.findByEmail(principal)
                .or(() -> profileRepository.findByEmail(auth.getName()))
                .orElse(null);
        }
    }

    private boolean isAdmin(Authentication auth, Profile profile) {
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return true;
        }
        if (auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))) {
            return true;
        }
        if (profile == null || profile.getRole() == null) return false;
        String role = profile.getRole().toLowerCase(Locale.ROOT);
        return role.equals("admin") || role.equals("super_admin") || role.equals("superadmin");
    }

    // ==================== REQUEST CLASSES ====================

    static class CreateReminderRequest {
        public String name;
        public String description;
        public String type;
        public String scheduleTime;
        public List<String> scheduleDays;
        public String timezone;
        public String targetAudience;
        public String message;
        public List<String> channels;
    }
}
