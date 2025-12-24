package com.schoolable.backend.messaging;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/messaging")
@Tag(name = "Messaging", description = "Channels and direct messaging")
public class MessagingController {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final ProfileRepository profileRepository;

    public MessagingController(
            ChannelRepository channelRepository,
            ChannelMemberRepository memberRepository,
            MessageRepository messageRepository,
            ProfileRepository profileRepository) {
        this.channelRepository = channelRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.profileRepository = profileRepository;
    }

    // ==================== CHANNELS ====================

    @Operation(summary = "Get all channels the current user is a member of")
    @GetMapping("/channels")
    public ResponseEntity<?> getMyChannels(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        List<Channel> channels = channelRepository.findChannelsByUserId(userId);
        List<Map<String, Object>> response = new ArrayList<>();

        for (Channel channel : channels) {
            Map<String, Object> channelData = buildChannelResponse(channel, userId);
            response.add(channelData);
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all public channels")
    @GetMapping("/channels/public")
    public ResponseEntity<?> getPublicChannels(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        List<Channel> channels = channelRepository.findByTypeOrderByNameAsc("public");
        List<Map<String, Object>> response = new ArrayList<>();

        for (Channel channel : channels) {
            Map<String, Object> channelData = buildChannelResponse(channel, userId);
            // Add joined status
            channelData.put("is_member", memberRepository.existsByChannelIdAndUserId(channel.getId(), userId));
            response.add(channelData);
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get channel details")
    @GetMapping("/channels/{channelId}")
    public ResponseEntity<?> getChannel(@PathVariable UUID channelId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Channel not found"));
        }

        Channel channel = channelOpt.get();
        
        // Check access for private channels
        if (!channel.getType().equals("public") && 
            !memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        return ResponseEntity.ok(buildChannelResponse(channel, userId));
    }

    @Operation(summary = "Create a new channel")
    @PostMapping("/channels")
    public ResponseEntity<?> createChannel(@RequestBody CreateChannelRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Validate type
        String type = req.type() != null ? req.type().toLowerCase() : "public";
        if (!List.of("public", "private", "dm").contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid channel type"));
        }

        // Create channel
        Channel channel = new Channel();
        channel.setName(req.name());
        channel.setType(type);
        channel.setCreatedBy(userId);
        channelRepository.save(channel);

        // Add creator as member
        addMemberToChannel(channel.getId(), userId);

        // Add initial members if provided
        if (req.memberIds() != null) {
            for (String memberId : req.memberIds()) {
                try {
                    UUID memberUuid = UUID.fromString(memberId);
                    if (!memberUuid.equals(userId)) { // Don't add creator twice
                        addMemberToChannel(channel.getId(), memberUuid);
                    }
                } catch (Exception e) {
                    // Skip invalid UUIDs
                }
            }
        }

        return ResponseEntity.ok(buildChannelResponse(channel, userId));
    }

    @Operation(summary = "Join a public channel")
    @PostMapping("/channels/{channelId}/join")
    public ResponseEntity<?> joinChannel(@PathVariable UUID channelId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Channel not found"));
        }

        Channel channel = channelOpt.get();
        if (!channel.getType().equals("public")) {
            return ResponseEntity.status(403).body(Map.of("error", "Can only join public channels"));
        }

        // Check if already member
        if (memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return ResponseEntity.ok(Map.of("message", "Already a member"));
        }

        addMemberToChannel(channelId, userId);
        return ResponseEntity.ok(Map.of("message", "Joined channel successfully"));
    }

    @Operation(summary = "Leave a channel")
    @PostMapping("/channels/{channelId}/leave")
    public ResponseEntity<?> leaveChannel(@PathVariable UUID channelId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var memberOpt = memberRepository.findByChannelIdAndUserId(channelId, userId);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not a member of this channel"));
        }

        memberRepository.delete(memberOpt.get());
        return ResponseEntity.ok(Map.of("message", "Left channel successfully"));
    }

    @Operation(summary = "Get channel members")
    @GetMapping("/channels/{channelId}/members")
    public ResponseEntity<?> getChannelMembers(@PathVariable UUID channelId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Check access
        if (!memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            var channelOpt = channelRepository.findById(channelId);
            if (channelOpt.isEmpty() || !channelOpt.get().getType().equals("public")) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
        }

        List<ChannelMember> members = memberRepository.findByChannelId(channelId);
        List<Map<String, Object>> response = new ArrayList<>();

        for (ChannelMember member : members) {
            var profileOpt = profileRepository.findById(member.getUserId());
            if (profileOpt.isPresent()) {
                Profile p = profileOpt.get();
                response.add(Map.of(
                    "user_id", p.getId(),
                    "full_name", p.getFullName() != null ? p.getFullName() : "",
                    "email", p.getEmail() != null ? p.getEmail() : "",
                    "avatar_url", getAvatarUrl(p),
                    "department", p.getDepartment() != null ? p.getDepartment() : "",
                    "joined_at", member.getJoinedAt()
                ));
            }
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Add members to a private channel")
    @PostMapping("/channels/{channelId}/members")
    public ResponseEntity<?> addMembers(
            @PathVariable UUID channelId,
            @RequestBody AddMembersRequest req,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Channel not found"));
        }

        // Only allow adding to private channels where user is already a member
        if (!memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        int added = 0;
        for (String memberId : req.userIds()) {
            try {
                UUID memberUuid = UUID.fromString(memberId);
                if (!memberRepository.existsByChannelIdAndUserId(channelId, memberUuid)) {
                    addMemberToChannel(channelId, memberUuid);
                    added++;
                }
            } catch (Exception e) {
                // Skip invalid UUIDs
            }
        }

        return ResponseEntity.ok(Map.of("added", added));
    }

    // ==================== DIRECT MESSAGES ====================

    @Operation(summary = "Get or create a DM channel with another user")
    @PostMapping("/dm/{otherUserId}")
    public ResponseEntity<?> getOrCreateDM(@PathVariable UUID otherUserId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        if (userId.equals(otherUserId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot create DM with yourself"));
        }

        // Check if other user exists
        if (!profileRepository.existsById(otherUserId)) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        // Find existing DM channel between these two users
        List<Channel> myDmChannels = channelRepository.findDmChannelsByUserId(userId);
        for (Channel channel : myDmChannels) {
            // Check if other user is also a member
            if (memberRepository.existsByChannelIdAndUserId(channel.getId(), otherUserId)) {
                // Found existing DM
                return ResponseEntity.ok(buildChannelResponse(channel, userId));
            }
        }

        // Create new DM channel
        Channel dm = new Channel();
        dm.setName("dm"); // Generic name, client will display other user's name
        dm.setType("dm");
        dm.setCreatedBy(userId);
        channelRepository.save(dm);

        // Add both users as members
        addMemberToChannel(dm.getId(), userId);
        addMemberToChannel(dm.getId(), otherUserId);

        return ResponseEntity.ok(buildChannelResponse(dm, userId));
    }

    // ==================== MESSAGES ====================

    @Operation(summary = "Get messages in a channel")
    @GetMapping("/channels/{channelId}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable UUID channelId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Check access
        var channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Channel not found"));
        }

        Channel channel = channelOpt.get();
        if (!channel.getType().equals("public") && 
            !memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        // Get messages (newest first for display reversal on client)
        List<Message> messages = messageRepository.findByChannelIdOrderByCreatedAtDesc(
            channelId, PageRequest.of(0, limit));

        List<Map<String, Object>> response = new ArrayList<>();
        for (Message msg : messages) {
            response.add(buildMessageResponse(msg));
        }

        // Reverse to get oldest first (correct order for chat display)
        Collections.reverse(response);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Send a message to a channel")
    @PostMapping("/channels/{channelId}/messages")
    public ResponseEntity<?> sendMessage(
            @PathVariable UUID channelId,
            @RequestBody SendMessageRequest req,
            Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Check access
        var channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Channel not found"));
        }

        Channel channel = channelOpt.get();
        
        // For public channels, auto-join if not a member
        if (channel.getType().equals("public") && 
            !memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            addMemberToChannel(channelId, userId);
        }
        
        // For private/dm channels, must be a member
        if (!channel.getType().equals("public") && 
            !memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a member of this channel"));
        }

        if (req.content() == null || req.content().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty"));
        }

        Message message = new Message();
        message.setChannelId(channelId);
        message.setUserId(userId);
        message.setContent(req.content().trim());
        messageRepository.save(message);

        return ResponseEntity.ok(buildMessageResponse(message));
    }

    // ==================== READ RECEIPTS ====================

    @Operation(summary = "Mark a channel as read (update last_read_at)")
    @PostMapping("/channels/{channelId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID channelId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var memberOpt = memberRepository.findByChannelIdAndUserId(channelId, userId);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a member of this channel"));
        }

        ChannelMember member = memberOpt.get();
        member.setLastReadAt(java.time.OffsetDateTime.now());
        memberRepository.save(member);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Get unread message count for a channel")
    @GetMapping("/channels/{channelId}/unread")
    public ResponseEntity<?> getUnreadCount(@PathVariable UUID channelId, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var memberOpt = memberRepository.findByChannelIdAndUserId(channelId, userId);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a member of this channel"));
        }

        ChannelMember member = memberOpt.get();
        java.time.OffsetDateTime lastRead = member.getLastReadAt();
        
        long unreadCount;
        if (lastRead == null) {
            // Never read before, count all messages
            unreadCount = messageRepository.countByChannelId(channelId);
        } else {
            // Count messages after last read
            unreadCount = messageRepository.countByChannelIdAndCreatedAtAfter(channelId, lastRead);
        }

        return ResponseEntity.ok(Map.of("unread_count", unreadCount));
    }

    // ==================== ONLINE STATUS ====================

    @Operation(summary = "Update online status (heartbeat)")
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }

        var profile = profileOpt.get();
        profile.setLastSeen(java.time.OffsetDateTime.now());
        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of("success", true, "last_seen", profile.getLastSeen()));
    }

    @Operation(summary = "Get online users (active in last 2 minutes)")
    @GetMapping("/online")
    public ResponseEntity<?> getOnlineUsers(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        java.time.OffsetDateTime threshold = java.time.OffsetDateTime.now().minusMinutes(2);
        List<Map<String, Object>> onlineUsers = new ArrayList<>();
        
        // Get all profiles that have been active in the last 2 minutes
        for (var profile : profileRepository.findAll()) {
            if (profile.getLastSeen() != null && profile.getLastSeen().isAfter(threshold)) {
                onlineUsers.add(Map.of(
                    "id", profile.getId(),
                    "full_name", profile.getFullName() != null ? profile.getFullName() : "",
                    "last_seen", profile.getLastSeen()
                ));
            }
        }

        return ResponseEntity.ok(onlineUsers);
    }

    // ==================== HELPER METHODS ====================

    private void addMemberToChannel(UUID channelId, UUID userId) {
        if (!memberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            ChannelMember member = new ChannelMember();
            member.setChannelId(channelId);
            member.setUserId(userId);
            memberRepository.save(member);
        }
    }

    private Map<String, Object> buildChannelResponse(Channel channel, UUID currentUserId) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", channel.getId());
        response.put("name", channel.getName());
        response.put("type", channel.getType());
        response.put("created_by", channel.getCreatedBy());
        response.put("created_at", channel.getCreatedAt());

        // Get member count
        long memberCount = memberRepository.countByChannelId(channel.getId());
        response.put("member_count", memberCount);

        // Get last message
        var lastMsgOpt = messageRepository.findFirstByChannelIdOrderByCreatedAtDesc(channel.getId());
        if (lastMsgOpt.isPresent()) {
            Message lastMsg = lastMsgOpt.get();
            response.put("last_message", lastMsg.getContent());
            response.put("last_message_at", lastMsg.getCreatedAt());
        }

        // For DM channels, get the other user's info
        if (channel.getType().equals("dm")) {
            List<ChannelMember> otherMembers = memberRepository.findOtherMembers(channel.getId(), currentUserId);
            if (!otherMembers.isEmpty()) {
                UUID otherUserId = otherMembers.get(0).getUserId();
                var otherProfileOpt = profileRepository.findById(otherUserId);
                if (otherProfileOpt.isPresent()) {
                    Profile other = otherProfileOpt.get();
                    Map<String, Object> otherUser = new HashMap<>();
                    otherUser.put("id", other.getId());
                    otherUser.put("full_name", other.getFullName());
                    otherUser.put("avatar_url", getAvatarUrl(other));
                    otherUser.put("department", other.getDepartment());
                    response.put("other_user", otherUser);
                    // For DMs, use other user's name as channel name
                    response.put("display_name", other.getFullName());
                }
            }
        } else {
            response.put("display_name", channel.getName());
        }

        // Get all members with their profiles
        List<ChannelMember> members = memberRepository.findByChannelId(channel.getId());
        List<Map<String, Object>> membersList = new ArrayList<>();
        for (ChannelMember m : members) {
            var profileOpt = profileRepository.findById(m.getUserId());
            if (profileOpt.isPresent()) {
                Profile p = profileOpt.get();
                membersList.add(Map.of(
                    "user_id", p.getId(),
                    "full_name", p.getFullName() != null ? p.getFullName() : "",
                    "avatar_url", getAvatarUrl(p),
                    "gender", p.getGender() != null ? p.getGender() : "",
                    "email", p.getEmail() != null ? p.getEmail() : "",
                    "employee_id", p.getEmployeeId() != null ? p.getEmployeeId() : ""
                ));
            }
        }
        response.put("members", membersList);

        return response;
    }

    private Map<String, Object> buildMessageResponse(Message msg) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", msg.getId());
        response.put("channel_id", msg.getChannelId());
        response.put("user_id", msg.getUserId());
        response.put("content", msg.getContent());
        response.put("created_at", msg.getCreatedAt());

        // Add sender info
        var senderOpt = profileRepository.findById(msg.getUserId());
        if (senderOpt.isPresent()) {
            Profile sender = senderOpt.get();
            Map<String, Object> senderInfo = new HashMap<>();
            senderInfo.put("id", sender.getId());
            senderInfo.put("full_name", sender.getFullName());
            senderInfo.put("avatar_url", getAvatarUrl(sender));
            senderInfo.put("email", sender.getEmail());
            response.put("sender", senderInfo);
        }

        return response;
    }

    private String getAvatarUrl(Profile p) {
        if (p.getAvatarUrl() != null && !p.getAvatarUrl().isEmpty()) {
            return p.getAvatarUrl();
        }
        String style = "bottts";
        if (p.getGender() != null) {
            if (p.getGender().equalsIgnoreCase("male")) style = "adventurer";
            else if (p.getGender().equalsIgnoreCase("female")) style = "adventurer-neutral";
        }
        String seed = p.getEmployeeId() != null ? p.getEmployeeId() : 
                (p.getEmail() != null ? p.getEmail() : "User");
        return "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
    }

    // ==================== REQUEST RECORDS ====================

    public record CreateChannelRequest(
            String name,
            String type,
            List<String> memberIds
    ) {}

    public record AddMembersRequest(List<String> userIds) {}

    public record SendMessageRequest(String content) {}
}
