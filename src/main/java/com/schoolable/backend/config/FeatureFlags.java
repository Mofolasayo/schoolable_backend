package com.schoolable.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlags {

    @Value("${schoolable.features.messaging-enabled:false}")
    private boolean messagingEnabled;

    public boolean isMessagingEnabled() {
        return messagingEnabled;
    }
}
