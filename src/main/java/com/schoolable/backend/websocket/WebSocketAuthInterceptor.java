package com.schoolable.backend.websocket;

import com.schoolable.backend.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Intercepts WebSocket messages to authenticate users via JWT.
 * 
 * Client must send JWT token in the "Authorization" header during CONNECT:
 * headers: { Authorization: "Bearer <token>" }
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Get Authorization header
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    Claims claims = jwtService.parse(token);
                    UUID userId = UUID.fromString(claims.getSubject());
                    String role = claims.get("role", String.class);
                    
                    // Create authentication
                    var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())) : List.of()
                    );
                    
                    // Set authentication in accessor (available throughout session)
                    accessor.setUser(new WebSocketPrincipal(userId, role));
                    
                    System.out.println("✅ WebSocket authenticated: " + userId);
                } catch (Exception e) {
                    System.out.println("❌ WebSocket auth failed: " + e.getMessage());
                    throw new IllegalArgumentException("Invalid token");
                }
            } else {
                System.out.println("⚠️ WebSocket connection without Authorization header");
                // Allow connection but mark as unauthenticated
                // Some endpoints might be public
            }
        }

        return message;
    }
}
