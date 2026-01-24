package com.schoolable.backend.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.notification.DeviceTokenRepository;
import com.schoolable.backend.notification.NotificationHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class NotificationsE2ETest extends BaseE2ETest {

    @Autowired
    private NotificationHistoryRepository notificationHistoryRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @BeforeEach
    void cleanup() {
        notificationHistoryRepository.deleteAll();
        deviceTokenRepository.deleteAll();
    }

    @Test
    void notificationsAndUnreadCountDefaultToEmpty() throws Exception {
        String userId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        var auth = new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());

        mockMvc.perform(get("/api/notifications")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications").isArray())
            .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(get("/api/notifications/unread-count")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
