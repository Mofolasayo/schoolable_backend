package com.schoolable.backend.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for sending push notifications via FCM.
 * 
 * Note: For production, you should use the Firebase Admin SDK.
 * This implementation uses the FCM HTTP v1 API directly for simplicity.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${fcm.server-key:}")
    private String fcmServerKey;

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    // Notification type constants
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TYPE_TASK_COMPLETED = "TASK_COMPLETED";
    public static final String TYPE_TASK_RATING = "TASK_RATING";
    public static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_PEER_RATING = "PEER_RATING";
    public static final String TYPE_PERFORMANCE = "PERFORMANCE";
    public static final String TYPE_COMPLIANCE = "COMPLIANCE";

    /**
     * Register a device token for push notifications.
     */
    @Transactional
    public DeviceToken registerDevice(UUID userId, String token, String platform, String deviceInfo) {
        // Check if token already exists
        Optional<DeviceToken> existing = deviceTokenRepository.findByUserIdAndToken(userId, token);
        if (existing.isPresent()) {
            DeviceToken dt = existing.get();
            dt.setIsActive(true);
            dt.setLastUsedAt(OffsetDateTime.now());
            if (deviceInfo != null) dt.setDeviceInfo(deviceInfo);
            return deviceTokenRepository.save(dt);
        }

        DeviceToken deviceToken = new DeviceToken(userId, token, platform.toLowerCase());
        deviceToken.setDeviceInfo(deviceInfo);
        return deviceTokenRepository.save(deviceToken);
    }

    /**
     * Unregister a device token.
     */
    @Transactional
    public void unregisterDevice(UUID userId, String token) {
        deviceTokenRepository.findByUserIdAndToken(userId, token)
            .ifPresent(dt -> {
                dt.setIsActive(false);
                deviceTokenRepository.save(dt);
            });
    }

    /**
     * Send notification to a single user.
     */
    public void sendToUser(UUID userId, String title, String body, String type, String referenceId, Map<String, Object> data) {
        // Save to history
        NotificationHistory history = new NotificationHistory(userId, title, body, type);
        history.setReferenceId(referenceId);
        if (data != null) {
            try {
                history.setData(objectMapper.writeValueAsString(data));
            } catch (JsonProcessingException e) {
                // Ignore
            }
        }
        historyRepository.save(history);

        // Send push notification if enabled
        if (fcmEnabled && fcmServerKey != null && !fcmServerKey.isEmpty()) {
            List<String> tokens = deviceTokenRepository.findActiveTokensByUserId(userId);
            for (String token : tokens) {
                sendFcmNotification(token, title, body, data);
            }
        }
    }

    /**
     * Send notification to multiple users.
     */
    public void sendToUsers(List<UUID> userIds, String title, String body, String type, String referenceId, Map<String, Object> data) {
        for (UUID userId : userIds) {
            sendToUser(userId, title, body, type, referenceId, data);
        }
    }

    /**
     * Send notification to a user when assigned a task.
     */
    public void notifyTaskAssigned(UUID assigneeId, Long taskId, String taskTitle, String assignerName) {
        String title = "New Task Assigned";
        String body = assignerName + " assigned you: " + taskTitle;
        
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "open_task");
        
        sendToUser(assigneeId, title, body, TYPE_TASK_ASSIGNED, taskId.toString(), data);
    }

    /**
     * Send notification when a task is completed.
     */
    public void notifyTaskCompleted(UUID creatorId, Long taskId, String taskTitle, String completedByName) {
        String title = "Task Completed";
        String body = completedByName + " completed: " + taskTitle;
        
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("action", "rate_task");
        
        sendToUser(creatorId, title, body, TYPE_TASK_COMPLETED, taskId.toString(), data);
    }

    /**
     * Send notification for new announcement.
     */
    public void notifyAnnouncement(List<UUID> userIds, Long announcementId, String title, String preview) {
        String notifTitle = "New Announcement";
        String body = title + (preview != null ? ": " + preview : "");
        
        Map<String, Object> data = new HashMap<>();
        data.put("announcementId", announcementId);
        data.put("action", "open_announcement");
        
        sendToUsers(userIds, notifTitle, body, TYPE_ANNOUNCEMENT, announcementId.toString(), data);
    }

    /**
     * Send notification for new message.
     */
    public void notifyNewMessage(UUID userId, String senderName, String channelName, String messagePreview) {
        String title = channelName != null ? channelName : senderName;
        String body = (channelName != null ? senderName + ": " : "") + messagePreview;
        
        Map<String, Object> data = new HashMap<>();
        data.put("action", "open_chat");
        
        sendToUser(userId, title, body, TYPE_MESSAGE, null, data);
    }

    /**
     * Send notification for pending peer rating.
     */
    public void notifyPendingPeerRating(UUID userId, int ratingCount) {
        String title = "Peer Rating Reminder";
        String body = "You have " + ratingCount + " colleague(s) to rate";
        
        Map<String, Object> data = new HashMap<>();
        data.put("action", "open_peer_rating");
        
        sendToUser(userId, title, body, TYPE_PEER_RATING, null, data);
    }

    /**
     * Get unread notification count for a user.
     */
    public long getUnreadCount(UUID userId) {
        return historyRepository.countUnreadByUserId(userId);
    }

    /**
     * Get notification history for a user.
     */
    public List<NotificationHistory> getNotificationHistory(UUID userId) {
        return historyRepository.findByUserIdOrderBySentAtDesc(userId);
    }

    /**
     * Mark notification as read.
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        historyRepository.markAsRead(notificationId);
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public void markAllAsRead(UUID userId) {
        historyRepository.markAllAsReadByUserId(userId);
    }

    /**
     * Send FCM notification (HTTP v1 legacy API).
     * For production, use Firebase Admin SDK.
     */
    private void sendFcmNotification(String token, String title, String body, Map<String, Object> data) {
        try {
            URL url = new URL("https://fcm.googleapis.com/fcm/send");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "key=" + fcmServerKey);
            conn.setDoOutput(true);

            Map<String, Object> message = new HashMap<>();
            message.put("to", token);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", title);
            notification.put("body", body);
            notification.put("sound", "default");
            message.put("notification", notification);
            
            if (data != null && !data.isEmpty()) {
                message.put("data", data);
            }

            String jsonPayload = objectMapper.writeValueAsString(message);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.warn("FCM notification failed with code: {}", responseCode);
                // Could mark token as invalid if 404
                if (responseCode == 404) {
                    deviceTokenRepository.deactivateToken(token);
                }
            }

            conn.disconnect();
        } catch (Exception e) {
            log.warn("Failed to send FCM notification: {}", e.getMessage());
        }
    }
}
