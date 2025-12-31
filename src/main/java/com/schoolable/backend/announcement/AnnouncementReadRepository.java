package com.schoolable.backend.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementReadId> {
    
    // Find all announcement IDs that a user has read
    List<AnnouncementRead> findByUserId(UUID userId);
    
    // Check if user has read a specific announcement
    boolean existsByUserIdAndAnnouncementId(UUID userId, UUID announcementId);

    // Count announcements read by a user after a certain date (for Aura calculation)
    long countByUserIdAndReadAtAfter(UUID userId, OffsetDateTime afterDate);

    // Count total announcements in a period (for engagement percentage)
    @Query("SELECT COUNT(a) FROM Announcement a WHERE a.createdAt >= :afterDate")
    long countTotalAnnouncementsAfter(@Param("afterDate") OffsetDateTime afterDate);
}
