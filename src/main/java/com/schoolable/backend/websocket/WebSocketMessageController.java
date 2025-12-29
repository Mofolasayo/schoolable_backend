package com.schoolable.backend.websocket;

import com.schoolable.backend.messaging.ChannelMemberRepository;
import com.schoolable.backend.messaging.Message;
import com.schoolable.backend.messaging.MessageRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

/**
 * WebSocket message controller for real-time chat functionality.
 * 
 * Message Flow:
 * 1. Client sends message to: /app/chat/{channelId}
 * 2. Server saves to DB and broadcasts to: /topic/channel/{channelId}
 * 3. All subscribers to that channel receive the message
 */
@Controller
public class WebSocketMessageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final ChannelMemberRepository memberRepository;
    private final ProfileRepository profileRepository;

    public WebSocketMessageController(
            SimpMessagingTemplate messagingTemplate,
            MessageRepository messageRepository,
            ChannelMemberRepository memberRepository,
            ProfileRepository profileRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.profileRepository = profileRepository;
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
        
        if (principal == null) {
            System.out.println("❌ Unauthenticated WebSocket message attempt");
            return;
        }

        UUID userId;
        if (principal instanceof WebSocketPrincipal wsp) {
            userId = wsp.getUserId();
        } else {
            userId = UUID.fromString(principal.getName());
        }

        UUID channelUuid = UUID.fromString(channelId);

        // Verify user is member of channel
        if (!memberRepository.existsByChannelIdAndUserId(channelUuid, userId)) {
            System.out.println("❌ User " + userId + " not member of channel " + channelId);
            return;
        }

        // Save message to database
        Message message = new Message();
        message.setChannelId(channelUuid);
        message.setUserId(userId);
        message.setContent(request.content());
        messageRepository.save(message);

        // Build response with sender info
        Map<String, Object> response = buildMessageResponse(message, userId);

        // Broadcast to all channel subscribers
        messagingTemplate.convertAndSend("/topic/channel/" + channelId, response);
        
        System.out.println("📨 Message broadcast to channel " + channelId);
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
        
        if (principal == null) return;

        UUID userId;
        if (principal instanceof WebSocketPrincipal wsp) {
            userId = wsp.getUserId();
        } else {
            userId = UUID.fromString(principal.getName());
        }

        // Get user's name
        String userName = "Someone";
        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isPresent()) {
            userName = profileOpt.get().getFullName();
        }

        Map<String, Object> response = Map.of(
            "userId", userId.toString(),
            "userName", userName,
            "isTyping", request.isTyping()
        );

        messagingTemplate.convertAndSend("/topic/channel/" + channelId + "/typing", response);
    }

    /**
     * Handle presence updates (user comes online/offline).
     * Client sends to: /app/presence
     * Broadcasts to: /topic/presence
     */
    @MessageMapping("/presence")
    public void handlePresence(@Payload PresenceRequest request, Principal principal) {
        if (principal == null) return;

        UUID userId;
        if (principal instanceof WebSocketPrincipal wsp) {
            userId = wsp.getUserId();
        } else {
            userId = UUID.fromString(principal.getName());
        }

        Map<String, Object> response = Map.of(
            "userId", userId.toString(),
            "status", request.status() // "online", "offline", "away"
        );

        messagingTemplate.convertAndSend("/topic/presence", response);
        System.out.println("👤 User " + userId + " is now " + request.status());
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

    private Map<String, Object> buildMessageResponse(Message msg, UUID senderId) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", msg.getId());
        response.put("channelId", msg.getChannelId().toString());
        response.put("userId", senderId.toString());
        response.put("content", msg.getContent());
        response.put("createdAt", msg.getCreatedAt().toString());

        // Add sender info
        var senderOpt = profileRepository.findById(senderId);
        if (senderOpt.isPresent()) {
            Profile sender = senderOpt.get();
            Map<String, Object> senderInfo = new HashMap<>();
            senderInfo.put("id", sender.getId().toString());
            senderInfo.put("fullName", sender.getFullName());
            senderInfo.put("avatarUrl", getAvatarUrl(sender));
            response.put("sender", senderInfo);
        }

        return response;
    }

    private String getAvatarUrl(Profile p) {
        if (p.getAvatarUrl() != null && !p.getAvatarUrl().isEmpty()) {
            return p.getAvatarUrl();
        }
        String style = "bottts";
        if (p.getGender() != null) {
            if (p.getGender().equalsIgnoreCase("male")) style = "adventurer";
            else if (p.getGender().equalsIgnoreCase("female")) style = "adventurer-neutral";
        }
        String seed = p.getEmployeeId() != null ? p.getEmployeeId() : 
                (p.getEmail() != null ? p.getEmail() : "User");
        return "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
    }

    // Request DTOs
    public record ChatMessageRequest(String content) {}
    public record TypingRequest(boolean isTyping) {}
    public record PresenceRequest(String status) {}
}
