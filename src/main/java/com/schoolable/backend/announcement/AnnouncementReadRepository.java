package com.schoolable.backend.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementReadId> {
    
    // Find all announcement IDs that a user has read
    List<AnnouncementRead> findByUserId(UUID userId);
    
    // Check if user has read a specific announcement
    boolean existsByUserIdAndAnnouncementId(UUID userId, UUID announcementId);
}
