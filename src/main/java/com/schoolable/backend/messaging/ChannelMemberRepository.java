package com.schoolable.backend.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    
    // Find all members of a channel
    List<ChannelMember> findByChannelId(UUID channelId);
    
    // Check if user is member of channel
    Optional<ChannelMember> findByChannelIdAndUserId(UUID channelId, UUID userId);
    
    // Check if user is member
    boolean existsByChannelIdAndUserId(UUID channelId, UUID userId);
    
    // Find all channel IDs for a user
    @Query("SELECT cm.channelId FROM ChannelMember cm WHERE cm.userId = :userId")
    List<UUID> findChannelIdsByUserId(@Param("userId") UUID userId);
    
    // Count members in a channel
    long countByChannelId(UUID channelId);
    
    // Find other members in a channel (excluding specific user)
    @Query("SELECT cm FROM ChannelMember cm WHERE cm.channelId = :channelId AND cm.userId != :userId")
    List<ChannelMember> findOtherMembers(@Param("channelId") UUID channelId, @Param("userId") UUID userId);
}
