package com.schoolable.backend.notifications;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SmartReminderAudienceService {

    private final ProfileRepository profileRepository;

    public SmartReminderAudienceService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public List<UUID> resolveTargetUserIds(String targetAudience) {
        String normalized = targetAudience == null ? "" : targetAudience.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "all".equals(normalized) || "pending_only".equals(normalized)) {
            return profileRepository.findByStatus("active").stream()
                .map(Profile::getId)
                .toList();
        }

        if (normalized.startsWith("department:") || normalized.startsWith("team:") || normalized.startsWith("specific_team:")) {
            String department = normalized.substring(normalized.indexOf(':') + 1).trim();
            if (department.isEmpty()) {
                return List.of();
            }
            return profileRepository.findByDepartmentAndStatus(department, "active").stream()
                .map(Profile::getId)
                .toList();
        }

        if ("team_leads".equals(normalized) || "team_lead".equals(normalized) || "team leads".equals(normalized)) {
            return profileRepository.findByIsTeamLeadTrue().stream()
                .filter(profile -> "active".equalsIgnoreCase(profile.getStatus()))
                .map(Profile::getId)
                .toList();
        }

        if (normalized.startsWith("specific_users:")) {
            normalized = normalized.substring("specific_users:".length());
        }

        List<UUID> userIds = new ArrayList<>();
        if (normalized.contains(",")) {
            for (String idStr : normalized.split(",")) {
                try {
                    userIds.add(UUID.fromString(idStr.trim()));
                } catch (Exception ignored) {
                    // skip invalid UUIDs
                }
            }
        } else {
            try {
                userIds.add(UUID.fromString(normalized));
            } catch (Exception ignored) {
                // no valid UUIDs
            }
        }

        return userIds;
    }
}
