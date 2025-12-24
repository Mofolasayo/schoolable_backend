package com.schoolable.backend.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {
    
    // Find all public channels
    List<Channel> findByTypeOrderByNameAsc(String type);
    
    // Find channels user is a member of (via ChannelMember)
    @Query("SELECT c FROM Channel c WHERE c.id IN " +
           "(SELECT cm.channelId FROM ChannelMember cm WHERE cm.userId = :userId) " +
           "ORDER BY c.createdAt DESC")
    List<Channel> findChannelsByUserId(@Param("userId") UUID userId);
    
    // Find DM channels where user is a member
    @Query("SELECT c FROM Channel c WHERE c.type = 'dm' AND c.id IN " +
           "(SELECT cm.channelId FROM ChannelMember cm WHERE cm.userId = :userId)")
    List<Channel> findDmChannelsByUserId(@Param("userId") UUID userId);
}
