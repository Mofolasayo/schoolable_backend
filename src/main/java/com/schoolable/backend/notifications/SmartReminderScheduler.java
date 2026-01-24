package com.schoolable.backend.notifications;

import com.schoolable.backend.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SmartReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SmartReminderScheduler.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Lagos");

    private final SmartReminderRepository reminderRepository;
    private final SmartReminderAudienceService audienceService;
    private final NotificationService notificationService;

    public SmartReminderScheduler(
        SmartReminderRepository reminderRepository,
        SmartReminderAudienceService audienceService,
        NotificationService notificationService
    ) {
        this.reminderRepository = reminderRepository;
        this.audienceService = audienceService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRateString = "${smartReminders.pollRateMs:60000}")
    public void dispatchReminders() {
        var reminders = reminderRepository.findByActiveTrue();
        if (reminders.isEmpty()) {
            return;
        }

        OffsetDateTime nowUtc = OffsetDateTime.now();
        for (SmartReminder reminder : reminders) {
            ZoneId zone = resolveZone(reminder.getTimezone());
            ZonedDateTime nowLocal = nowUtc.atZoneSameInstant(zone);
            if (!shouldTrigger(reminder, nowLocal)) {
                continue;
            }

            var targetUserIds = audienceService.resolveTargetUserIds(reminder.getTargetAudience());
            if (!targetUserIds.isEmpty()) {
                String title = "📢 " + reminder.getName();
                String body = reminder.getMessage();

                Map<String, Object> data = new HashMap<>();
                data.put("action", "open_announcement");
                data.put("reminderId", reminder.getId());
                data.put("type", "smart_reminder");

                for (UUID userId : targetUserIds) {
                    notificationService.sendToUser(
                        userId,
                        title,
                        body,
                        "SMART_REMINDER",
                        reminder.getId().toString(),
                        data
                    );
                }
            }

            reminder.setTriggerCount(reminder.getTriggerCount() + 1);
            reminder.setLastTriggered(OffsetDateTime.now());
            reminderRepository.save(reminder);
        }
    }

    private boolean shouldTrigger(SmartReminder reminder, ZonedDateTime nowLocal) {
        if (reminder.getScheduleTime() == null || reminder.getScheduleTime().isBlank()) {
            return false;
        }
        LocalTime scheduled;
        try {
            scheduled = LocalTime.parse(reminder.getScheduleTime());
        } catch (DateTimeParseException e) {
            log.warn("Invalid schedule time '{}' for reminder {}", reminder.getScheduleTime(), reminder.getId());
            return false;
        }

        if (nowLocal.getHour() != scheduled.getHour() || nowLocal.getMinute() != scheduled.getMinute()) {
            return false;
        }

        if (!matchesScheduleDay(reminder.getScheduleDays(), nowLocal.getDayOfWeek())) {
            return false;
        }

        if (reminder.getLastTriggered() != null) {
            ZonedDateTime lastLocal = reminder.getLastTriggered().atZoneSameInstant(nowLocal.getZone());
            if (lastLocal.truncatedTo(ChronoUnit.MINUTES).equals(nowLocal.truncatedTo(ChronoUnit.MINUTES))) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesScheduleDay(String scheduleDays, DayOfWeek dayOfWeek) {
        if (scheduleDays == null || scheduleDays.isBlank()) {
            return true;
        }
        String today = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
        for (String entry : scheduleDays.split(",")) {
            String normalized = entry.trim().toLowerCase(Locale.ENGLISH);
            if (!normalized.isEmpty() && normalized.equals(today)) {
                return true;
            }
        }
        return false;
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
