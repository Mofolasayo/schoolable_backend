package com.schoolable.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

/**
 * WebSocket controller for realtime notifications (tasks/announcements).
 * Chat endpoints are kept for compatibility but disabled in production.
 */
@Controller
public class WebSocketMessageController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMessageController.class);

    private final SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    @Lazy
    private NativeWebSocketConfig nativeWebSocketConfig;

    public WebSocketMessageController(
            SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handle incoming chat messages.
     * Client sends to: /app/chat/{channelId}
     * Broadcasts to: /topic/channel/{channelId}
     */
    @MessageMapping("/chat/{channelId}")
    public void handleMessage(
            @DestinationVariable String channelId,
            @Payload ChatMessageRequest request,
            Principal principal) {
        
        log.info("Chat messaging disabled. Ignoring message for channel {}", channelId);
    }

    /**
     * Handle typing indicators.
     * Client sends to: /app/typing/{channelId}
     * Broadcasts to: /topic/channel/{channelId}/typing
     */
    @MessageMapping("/typing/{channelId}")
    public void handleTyping(
            @DestinationVariable String channelId,
            @Payload TypingRequest request,
            Principal principal) {
        
        log.info("Chat typing indicators disabled. Ignoring typing for channel {}", channelId);
    }

    /**
     * Handle presence updates (user comes online/offline).
     * Client sends to: /app/presence
     * Broadcasts to: /topic/presence
     */
    @MessageMapping("/presence")
    public void handlePresence(@Payload PresenceRequest request, Principal principal) {
        log.info("Chat presence updates disabled. Ignoring presence update.");
    }

    /**
     * Send a notification to a specific user.
     * This is called from other services when they need to notify a user.
     */
    public void sendNotificationToUser(UUID userId, String type, Map<String, Object> data) {
        Map<String, Object> notification = new HashMap<>(data);
        notification.put("type", type);
        notification.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/queue/notifications",
            notification
        );
    }

    /**
     * Broadcast a message to a specific channel (for use from REST endpoints).
     */
    public void broadcastToChannel(UUID channelId, Map<String, Object> message) {
        messagingTemplate.convertAndSend("/topic/channel/" + channelId, message);
    }

    /**
     * Broadcast task updates to all connected clients.
     * Called when tasks are created, updated, or deleted.
     */
    public void broadcastTaskUpdate(String action, Long taskId, Map<String, Object> taskData) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NOTIFICATION");
        notification.put("notificationType", "task_" + action);
        notification.put("taskId", taskId);
        notification.put("data", taskData);
        notification.put("timestamp", System.currentTimeMillis());

        // Broadcast to STOMP clients
        messagingTemplate.convertAndSend("/topic/tasks", notification);
        
        // Broadcast to native WebSocket clients
        if (nativeWebSocketConfig != null) {
            nativeWebSocketConfig.broadcastToTopic("/topic/tasks", notification);
        }

        log.info("Task {} broadcast for task {}", action, taskId);
    }

    /**
     * Broadcast announcement updates to all connected clients.
     */
    public void broadcastAnnouncementUpdate(String action, Long announcementId, Map<String, Object> announcementData) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NOTIFICATION");
        notification.put("notificationType", "announcement_" + action);
        notification.put("announcementId", announcementId);
        notification.put("data", announcementData);
        notification.put("timestamp", System.currentTimeMillis());

        // Broadcast to STOMP clients
        messagingTemplate.convertAndSend("/topic/announcements", notification);
        
        // Broadcast to native WebSocket clients
        if (nativeWebSocketConfig != null) {
            nativeWebSocketConfig.broadcastToTopic("/topic/announcements", notification);
        }

        log.info("Announcement {} broadcast for announcement {}", action, announcementId);
    }

    // Request DTOs
    public record ChatMessageRequest(String content) {}
    public record TypingRequest(boolean isTyping) {}
    public record PresenceRequest(String status) {}
}
