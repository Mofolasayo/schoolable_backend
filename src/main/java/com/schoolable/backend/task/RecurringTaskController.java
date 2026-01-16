package com.schoolable.backend.task;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/tasks/recurring", "/tasks/recurring"})
public class RecurringTaskController {

    private final RecurringTaskTemplateRepository templateRepository;
    private final ProfileRepository profileRepository;

    public RecurringTaskController(RecurringTaskTemplateRepository templateRepository, ProfileRepository profileRepository) {
        this.templateRepository = templateRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication auth, @RequestParam(required = false) String department) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (!isAdmin(auth) && (profile == null || !Boolean.TRUE.equals(profile.getIsTeamLead()))) {
            return ResponseEntity.status(403).body(Map.of("error", "Team lead or admin access required"));
        }

        String dept = department != null ? department : (profile != null ? profile.getDepartment() : null);
        List<RecurringTaskTemplate> templates = dept != null
            ? templateRepository.findByOrganizationAndIsActiveTrue(dept)
            : templateRepository.findByIsActiveTrueOrderByCreatedAtDesc();

        return ResponseEntity.ok(templates);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRecurringTemplateRequest request, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (!isAdmin(auth) && (profile == null || !Boolean.TRUE.equals(profile.getIsTeamLead()))) {
            return ResponseEntity.status(403).body(Map.of("error", "Team lead or admin access required"));
        }

        String title = normalizeString(request.title());
        if (title == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        String recurrencePattern = normalizeString(request.recurrencePattern());
        if (recurrencePattern == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recurrence pattern is required"));
        }

        String department = normalizeString(request.organization());
        if (department == null && profile != null) {
            department = profile.getDepartment();
        }

        UUID assigneeId;
        try {
            assigneeId = parseUuid(request.defaultAssigneeId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "defaultAssigneeId must be a valid UUID"));
        }

        LocalTime dueTime;
        try {
            dueTime = parseTime(request.dueTime());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "dueTime must be in HH:mm format"));
        }

        LocalDate nextOccurrence;
        try {
            nextOccurrence = parseDate(request.nextOccurrence());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "nextOccurrence must be in yyyy-MM-dd format"));
        }

        RecurringTaskTemplate template = new RecurringTaskTemplate();
        template.setTitle(title);
        template.setDescription(normalizeString(request.description()));
        template.setDefaultPriority(normalizeString(request.defaultPriority()) != null ? normalizeString(request.defaultPriority()) : "Medium");
        template.setDefaultAssigneeId(assigneeId);
        template.setOrganization(department);
        template.setTags(request.tags() != null ? request.tags().toArray(new String[0]) : null);
        template.setRecurrencePattern(recurrencePattern);
        template.setRecurrenceDay(request.recurrenceDay());
        template.setRecurrenceDays(request.recurrenceDays() != null ? request.recurrenceDays().toArray(new Integer[0]) : null);
        template.setDueTime(dueTime);
        template.setDaysUntilDue(request.daysUntilDue() != null ? request.daysUntilDue() : 1);
        LocalDate resolvedNextOccurrence = nextOccurrence != null
            ? nextOccurrence
            : template.computeNextOccurrence(LocalDate.now(), true);
        template.setNextOccurrence(resolvedNextOccurrence);
        template.setCreatedBy(userId);
        template.setIsActive(true);

        RecurringTaskTemplate saved = templateRepository.save(template);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody CreateRecurringTemplateRequest request, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (!isAdmin(auth) && (profile == null || !Boolean.TRUE.equals(profile.getIsTeamLead()))) {
            return ResponseEntity.status(403).body(Map.of("error", "Team lead or admin access required"));
        }

        RecurringTaskTemplate template = templateRepository.findById(id).orElse(null);
        if (template == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Template not found"));
        }

        boolean recurrenceChanged = false;

        if (request.title() != null) {
            String normalizedTitle = normalizeString(request.title());
            if (normalizedTitle == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be blank"));
            }
            template.setTitle(normalizedTitle);
        }
        if (request.description() != null) template.setDescription(normalizeString(request.description()));
        if (request.defaultPriority() != null) template.setDefaultPriority(normalizeString(request.defaultPriority()));
        if (request.defaultAssigneeId() != null) {
            try {
                template.setDefaultAssigneeId(parseUuid(request.defaultAssigneeId()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "defaultAssigneeId must be a valid UUID"));
            }
        }
        if (request.organization() != null) template.setOrganization(normalizeString(request.organization()));
        if (request.tags() != null) template.setTags(request.tags().toArray(new String[0]));
        if (request.recurrencePattern() != null) {
            String pattern = normalizeString(request.recurrencePattern());
            if (pattern == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Recurrence pattern cannot be blank"));
            }
            template.setRecurrencePattern(pattern);
            recurrenceChanged = true;
        }
        if (request.recurrenceDay() != null) {
            template.setRecurrenceDay(request.recurrenceDay());
            recurrenceChanged = true;
        }
        if (request.recurrenceDays() != null) {
            template.setRecurrenceDays(request.recurrenceDays().toArray(new Integer[0]));
            recurrenceChanged = true;
        }
        if (request.dueTime() != null) {
            try {
                template.setDueTime(parseTime(request.dueTime()));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "dueTime must be in HH:mm format"));
            }
        }
        if (request.daysUntilDue() != null) template.setDaysUntilDue(request.daysUntilDue());
        if (request.nextOccurrence() != null) {
            try {
                template.setNextOccurrence(parseDate(request.nextOccurrence()));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "nextOccurrence must be in yyyy-MM-dd format"));
            }
        } else if (recurrenceChanged) {
            template.setNextOccurrence(template.computeNextOccurrence(LocalDate.now(), true));
        }
        if (request.isActive() != null) template.setIsActive(request.isActive());

        RecurringTaskTemplate saved = templateRepository.save(template);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        RecurringTaskTemplate template = templateRepository.findById(id).orElse(null);
        if (template == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Template not found"));
        }
        template.setIsActive(false);
        templateRepository.save(template);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private String normalizeString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UUID parseUuid(String value) {
        String trimmed = normalizeString(value);
        if (trimmed == null) return null;
        return UUID.fromString(trimmed);
    }

    private LocalTime parseTime(String value) {
        String trimmed = normalizeString(value);
        if (trimmed == null) return null;
        return LocalTime.parse(trimmed);
    }

    private LocalDate parseDate(String value) {
        String trimmed = normalizeString(value);
        if (trimmed == null) return null;
        return LocalDate.parse(trimmed);
    }

    public record CreateRecurringTemplateRequest(
        String title,
        String description,
        String defaultPriority,
        String defaultAssigneeId,
        String organization,
        List<String> tags,
        String recurrencePattern,
        Integer recurrenceDay,
        List<Integer> recurrenceDays,
        String dueTime,
        Integer daysUntilDue,
        String nextOccurrence,
        Boolean isActive
    ) {}
}
