package com.schoolable.backend.performance;

import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

@Service
public class PeerHelpfulnessReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PeerHelpfulnessReminderScheduler.class);

    private final PeerHelpfulnessRepository helpfulnessRepository;
    private final ProfileRepository profileRepository;
    private final NotificationService notificationService;

    public PeerHelpfulnessReminderScheduler(
        PeerHelpfulnessRepository helpfulnessRepository,
        ProfileRepository profileRepository,
        NotificationService notificationService
    ) {
        this.helpfulnessRepository = helpfulnessRepository;
        this.profileRepository = profileRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${peerHelpfulness.reminderCron:0 0 9 * * MON-FRI}", zone = "Africa/Lagos")
    public void sendPeerRatingReminders() {
        List<Profile> profiles = profileRepository.findAll().stream()
            .filter(this::isEligibleEmployee)
            .toList();

        if (profiles.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now();
        int weekNumber = today.get(WeekFields.ISO.weekOfYear());
        int year = today.getYear();

        var profilesByDepartment = profiles.stream()
            .filter(p -> p.getDepartment() != null && !p.getDepartment().isBlank())
            .collect(java.util.stream.Collectors.groupingBy(Profile::getDepartment));

        for (Profile profile : profiles) {
            String department = profile.getDepartment();
            if (department == null || department.isBlank()) {
                continue;
            }

            List<Profile> peers = profilesByDepartment.getOrDefault(department, List.of()).stream()
                .filter(peer -> !peer.getId().equals(profile.getId()))
                .toList();

            if (peers.isEmpty()) {
                continue;
            }

            long ratingsGiven = helpfulnessRepository.countByRaterIdAndWeekNumberAndYear(
                profile.getId(), weekNumber, year
            );
            int pending = (int) Math.max(0, peers.size() - ratingsGiven);
            if (pending <= 0) {
                continue;
            }

            notificationService.notifyPendingPeerRating(profile.getId(), pending);
        }
    }

    private boolean isEligibleEmployee(Profile profile) {
        if (profile == null) {
            return false;
        }
        String status = profile.getStatus();
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals("active") && !normalized.equals("approved")) {
                return false;
            }
        }

        String role = profile.getRole();
        if (role == null) {
            return true;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return !(normalized.equals("admin")
            || normalized.equals("super_admin")
            || normalized.equals("super admin")
            || normalized.equals("superadmin"));
    }
}
