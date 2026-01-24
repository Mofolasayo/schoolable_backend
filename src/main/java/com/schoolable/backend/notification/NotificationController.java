package com.schoolable.backend.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for push notification management.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    /**
     * Register a device for push notifications.
     */
    @PostMapping("/register-device")
    public ResponseEntity<?> registerDevice(
            Authentication auth,
            @RequestBody RegisterDeviceRequest request
    ) {
        UUID userId = getUserId(auth);
        
        DeviceToken token = notificationService.registerDevice(
            userId,
            request.token(),
            request.platform(),
            request.deviceInfo()
        );

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Device registered successfully",
            "deviceId", token.getId()
        ));
    }

    /**
     * Unregister a device.
     */
    @PostMapping("/unregister-device")
    public ResponseEntity<?> unregisterDevice(
            Authentication auth,
            @RequestBody UnregisterDeviceRequest request
    ) {
        UUID userId = getUserId(auth);
        notificationService.unregisterDevice(userId, request.token());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Device unregistered successfully"
        ));
    }

    /**
     * Get notification history for the current user.
     */
    @GetMapping
    public ResponseEntity<?> getNotifications(Authentication auth) {
        UUID userId = getUserId(auth);
        
        List<NotificationHistory> notifications = notificationService.getNotificationHistory(userId);
        long unreadCount = notificationService.getUnreadCount(userId);

        return ResponseEntity.ok(Map.of(
            "notifications", notifications.stream().map(this::toDto).collect(Collectors.toList()),
            "unreadCount", unreadCount
        ));
    }

    /**
     * Get unread notification count.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication auth) {
        UUID userId = getUserId(auth);
        long count = notificationService.getUnreadCount(userId);

        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * Mark a notification as read.
     */
    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markAsRead(
            Authentication auth,
            @PathVariable Long id
    ) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Mark all notifications as read.
     */
    @PostMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<?> markAllAsRead(Authentication auth) {
        UUID userId = getUserId(auth);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Get registered devices for the current user.
     */
    @GetMapping("/devices")
    public ResponseEntity<?> getDevices(Authentication auth) {
        UUID userId = getUserId(auth);
        List<DeviceToken> devices = deviceTokenRepository.findByUserIdAndIsActiveTrue(userId);

        return ResponseEntity.ok(Map.of(
            "devices", devices.stream().map(d -> Map.of(
                "id", d.getId(),
                "platform", d.getPlatform(),
                "lastUsedAt", d.getLastUsedAt(),
                "createdAt", d.getCreatedAt()
            )).collect(Collectors.toList())
        ));
    }

    private Map<String, Object> toDto(NotificationHistory n) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", n.getId());
        dto.put("title", n.getTitle());
        dto.put("body", n.getBody());
        dto.put("type", n.getType());
        dto.put("referenceId", n.getReferenceId());
        dto.put("isRead", n.getIsRead());
        dto.put("sentAt", n.getSentAt());
        dto.put("readAt", n.getReadAt());
        dto.put("data", n.getData());
        return dto;
    }

    private UUID getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(principal.toString());
    }

    // Request DTOs
    record RegisterDeviceRequest(String token, String platform, String deviceInfo) {}
    record UnregisterDeviceRequest(String token) {}
}
