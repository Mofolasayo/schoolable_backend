package com.schoolable.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.auth.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native WebSocket configuration for clients that don't support STOMP.
 * This handler bridges native WebSocket connections to the STOMP message broker.
 */
@Configuration
@EnableWebSocket
public class NativeWebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(NativeWebSocketConfig.class);

    private final JwtService jwtService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Track authenticated sessions: sessionId -> userId
    private final Map<String, UUID> authenticatedSessions = new ConcurrentHashMap<>();
    // Track session subscriptions: sessionId -> set of topics
    private final Map<String, java.util.Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    // Track all sessions for broadcasting
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public NativeWebSocketConfig(JwtService jwtService, SimpMessagingTemplate messagingTemplate) {
        this.jwtService = jwtService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new NativeWebSocketHandler(), "/ws-native")
                .setAllowedOrigins("*");
    }

    private class NativeWebSocketHandler extends TextWebSocketHandler {
        
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("Native WebSocket connected: {}", session.getId());
            sessions.put(session.getId(), session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload();
            log.debug("Native WS message: {}", payload);

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                String type = (String) data.get("type");

                if (type == null) {
                    sendError(session, "Missing message type");
                    return;
                }

                switch (type) {
                    case "AUTH":
                        handleAuth(session, data);
                        break;
                    case "PING":
                        handlePing(session);
                        break;
                    case "SUBSCRIBE":
                        handleSubscribe(session, data);
                        break;
                    case "UNSUBSCRIBE":
                        handleUnsubscribe(session, data);
                        break;
                    default:
                        // For authenticated users, forward other messages
                        if (!authenticatedSessions.containsKey(session.getId())) {
                            sendError(session, "Not authenticated");
                            return;
                        }
                        // Handle chat messages, etc.
                        handleUserMessage(session, type, data);
                }
            } catch (Exception e) {
                log.warn("Error handling native WS message: {}", e.getMessage());
                sendError(session, "Invalid message format");
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            log.info("Native WebSocket closed: {}", session.getId());
            authenticatedSessions.remove(session.getId());
            sessionSubscriptions.remove(session.getId());
            sessions.remove(session.getId());
        }

        private void handleAuth(WebSocketSession session, Map<String, Object> data) throws IOException {
            String token = (String) data.get("token");
            if (token == null || token.isEmpty()) {
                sendMessage(session, Map.of("type", "AUTH_FAILED", "error", "Missing token"));
                return;
            }

            try {
                UUID userId = jwtService.extractUserId(token);
                if (userId != null && jwtService.isExpired(token) == false) {
                    authenticatedSessions.put(session.getId(), userId);
                    sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
                    
                    // Auto-subscribe to task and announcement updates
                    sessionSubscriptions.get(session.getId()).add("/topic/tasks");
                    sessionSubscriptions.get(session.getId()).add("/topic/announcements");
                    
                    sendMessage(session, Map.of("type", "AUTH_SUCCESS", "userId", userId.toString()));
                    log.info("Native WS authenticated: {}", userId);
                } else {
                    sendMessage(session, Map.of("type", "AUTH_FAILED", "error", "Invalid token"));
                }
            } catch (Exception e) {
                sendMessage(session, Map.of("type", "AUTH_FAILED", "error", "Token validation failed"));
            }
        }

        private void handlePing(WebSocketSession session) throws IOException {
            sendMessage(session, Map.of("type", "PONG"));
        }

        private void handleSubscribe(WebSocketSession session, Map<String, Object> data) throws IOException {
            if (!authenticatedSessions.containsKey(session.getId())) {
                sendError(session, "Not authenticated");
                return;
            }

            String topic = (String) data.get("topic");
            if (topic != null) {
                sessionSubscriptions.get(session.getId()).add(topic);
                sendMessage(session, Map.of("type", "SUBSCRIBED", "topic", topic));
            }
        }

        private void handleUnsubscribe(WebSocketSession session, Map<String, Object> data) throws IOException {
            if (!authenticatedSessions.containsKey(session.getId())) {
                return;
            }

            String topic = (String) data.get("topic");
            if (topic != null) {
                sessionSubscriptions.get(session.getId()).remove(topic);
            }
        }

        private void handleUserMessage(WebSocketSession session, String type, Map<String, Object> data) {
            // Forward to STOMP broker for processing
            UUID userId = authenticatedSessions.get(session.getId());
            if (userId == null) return;

            // Add userId to data
            data.put("userId", userId.toString());
            
            // Route based on type
            switch (type) {
                case "CHAT_MESSAGE":
                    log.debug("Chat messaging disabled. Ignoring native WS chat message.");
                    break;
                case "TYPING":
                    log.debug("Chat messaging disabled. Ignoring native WS typing update.");
                    break;
            }
        }

        private void sendMessage(WebSocketSession session, Map<String, Object> data) throws IOException {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            }
        }

        private void sendError(WebSocketSession session, String error) throws IOException {
            sendMessage(session, Map.of("type", "ERROR", "error", error));
        }
    }

    /**
     * Broadcast a message to all authenticated native WebSocket clients subscribed to a topic.
     */
    public void broadcastToTopic(String topic, Map<String, Object> message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("Failed to serialize broadcast message: {}", e.getMessage());
            return;
        }

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            
            // Check if session is subscribed to this topic
            java.util.Set<String> subs = sessionSubscriptions.get(sessionId);
            if (subs != null && subs.contains(topic) && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (Exception e) {
                    log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }
}
