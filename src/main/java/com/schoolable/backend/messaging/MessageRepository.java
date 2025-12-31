package com.schoolable.backend.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {
    
    // Find messages in a channel ordered by creation time (newest first)
    List<Message> findByChannelIdOrderByCreatedAtDesc(UUID channelId, Pageable pageable);
    
    // Find messages in a channel (oldest first for display)
    List<Message> findByChannelIdOrderByCreatedAtAsc(UUID channelId);
    
    // Find last message in a channel
    Optional<Message> findFirstByChannelIdOrderByCreatedAtDesc(UUID channelId);
    
    // Count messages in a channel
    long countByChannelId(UUID channelId);
    
    // Count messages in a channel after a certain time (for unread count)
    long countByChannelIdAndCreatedAtAfter(UUID channelId, OffsetDateTime after);
    
    // Find messages after a certain ID (for pagination/sync)
    @Query("SELECT m FROM Message m WHERE m.channelId = :channelId AND m.id > :afterId ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfter(@Param("channelId") UUID channelId, @Param("afterId") Long afterId);

    // Count messages sent by a user after a date (for Communication score)
    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime after);

    // Count total messages in the system after a date (for comparison)
    long countByCreatedAtAfter(OffsetDateTime after);
}

