package com.schoolable.backend.websocket;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal implementation for WebSocket sessions.
 * Holds the authenticated user's ID and role.
 */
public class WebSocketPrincipal implements Principal {
    
    private final UUID userId;
    private final String role;

    public WebSocketPrincipal(UUID userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }
}
