package com.schoolable.backend.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.schoolable.backend.settings.UserPreference;
import com.schoolable.backend.settings.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for sending push notifications via FCM.
 * 
 * This implementation supports Firebase Admin SDK (HTTP v1) with
 * a fallback to the legacy server key if configured.
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

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Value("${fcm.server-key:}")
    private String fcmServerKey;

    @Value("${fcm.enabled:false}")
    private boolean fcmEnabled;

    @Value("${fcm.service-account-path:}")
    private String fcmServiceAccountPath;

    private FirebaseMessaging firebaseMessaging;
    private boolean firebaseReady = false;

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

    @PostConstruct
    public void initFirebase() {
        if (!fcmEnabled) {
            return;
        }
        try {
            FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(existing -> "schoolable".equals(existing.getName()))
                .findFirst()
                .orElse(null);

            if (app == null) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(loadCredentials())
                    .build();
                app = FirebaseApp.initializeApp(options, "schoolable");
            }

            firebaseMessaging = FirebaseMessaging.getInstance(app);
            firebaseReady = true;
            log.info("Firebase Admin initialized for push notifications.");
        } catch (Exception e) {
            firebaseReady = false;
            log.warn("Firebase Admin initialization failed; will fallback to legacy if configured.", e);
        }
    }

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

        if (!isPushEnabled(userId)) {
            return;
        }

        // Send push notification if enabled
        if (fcmEnabled && firebaseReady) {
            List<String> tokens = deviceTokenRepository.findActiveTokensByUserId(userId);
            for (String token : tokens) {
                sendFcmNotificationV1(token, title, body, data);
            }
        } else if (fcmEnabled && fcmServerKey != null && !fcmServerKey.isEmpty()) {
            List<String> tokens = deviceTokenRepository.findActiveTokensByUserId(userId);
            for (String token : tokens) {
                sendFcmNotificationLegacy(token, title, body, data);
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

    private void sendFcmNotificationV1(String token, String title, String body, Map<String, Object> data) {
        if (!firebaseReady || firebaseMessaging == null) {
            return;
        }
        try {
            Notification.Builder notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);

            Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(notificationBuilder.build());

            if (data != null && !data.isEmpty()) {
                Map<String, String> stringData = new HashMap<>();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() != null) {
                        stringData.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
                builder.putAllData(stringData);
            }

            firebaseMessaging.send(builder.build());
        } catch (FirebaseMessagingException e) {
            log.warn("FCM v1 notification failed: {}", e.getMessage());
        }
    }

    /**
     * Send FCM notification using legacy server key.
     * For production, prefer Firebase Admin SDK.
     */
    private void sendFcmNotificationLegacy(String token, String title, String body, Map<String, Object> data) {
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
                if (responseCode == 404) {
                    deviceTokenRepository.deactivateToken(token);
                }
            }

            conn.disconnect();
        } catch (Exception e) {
            log.warn("Failed to send FCM notification: {}", e.getMessage());
        }
    }

    private boolean isPushEnabled(UUID userId) {
        try {
            Optional<UserPreference> pref = userPreferenceRepository.findById(userId);
            if (pref.isPresent()) {
                Boolean enabled = pref.get().getPushNotifications();
                if (enabled != null && !enabled) {
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check push preference; defaulting to enabled.", e);
        }
        return true;
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (fcmServiceAccountPath != null && !fcmServiceAccountPath.isBlank()) {
            try (FileInputStream serviceAccount = new FileInputStream(fcmServiceAccountPath)) {
                return GoogleCredentials.fromStream(serviceAccount);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
