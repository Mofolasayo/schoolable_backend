package com.schoolable.backend.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    
    // Find all published or due scheduled announcements
    @Query("SELECT a FROM Announcement a WHERE a.status = 'Published' OR (a.status = 'Scheduled' AND a.scheduledAt <= CURRENT_TIMESTAMP) ORDER BY a.createdAt DESC")
    List<Announcement> findActiveAnnouncements();
    
    // Find announcements by author
    List<Announcement> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
    
    // Find announcements by status
    List<Announcement> findByStatusOrderByCreatedAtDesc(String status);
    
    // Find announcements by audience
    @Query("SELECT a FROM Announcement a WHERE a.audience = :audience OR a.audience = 'All Staff' ORDER BY a.createdAt DESC")
    List<Announcement> findByAudienceOrAllStaff(@Param("audience") String audience);
}
