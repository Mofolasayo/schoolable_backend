package com.schoolable.backend.task;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
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

        String department = request.organization() != null ? request.organization() : (profile != null ? profile.getDepartment() : null);

        RecurringTaskTemplate template = new RecurringTaskTemplate();
        template.setTitle(request.title());
        template.setDescription(request.description());
        template.setDefaultPriority(request.defaultPriority() != null ? request.defaultPriority() : "Medium");
        template.setDefaultAssigneeId(request.defaultAssigneeId() != null ? UUID.fromString(request.defaultAssigneeId()) : null);
        template.setOrganization(department);
        template.setTags(request.tags() != null ? request.tags().toArray(new String[0]) : null);
        template.setRecurrencePattern(request.recurrencePattern());
        template.setRecurrenceDay(request.recurrenceDay());
        template.setDueTime(request.dueTime() != null ? LocalTime.parse(request.dueTime()) : null);
        template.setDaysUntilDue(request.daysUntilDue() != null ? request.daysUntilDue() : 1);
        template.setNextOccurrence(request.nextOccurrence() != null ? LocalDate.parse(request.nextOccurrence()) : LocalDate.now());
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

        if (request.title() != null) template.setTitle(request.title());
        if (request.description() != null) template.setDescription(request.description());
        if (request.defaultPriority() != null) template.setDefaultPriority(request.defaultPriority());
        if (request.defaultAssigneeId() != null) template.setDefaultAssigneeId(UUID.fromString(request.defaultAssigneeId()));
        if (request.organization() != null) template.setOrganization(request.organization());
        if (request.tags() != null) template.setTags(request.tags().toArray(new String[0]));
        if (request.recurrencePattern() != null) template.setRecurrencePattern(request.recurrencePattern());
        if (request.recurrenceDay() != null) template.setRecurrenceDay(request.recurrenceDay());
        if (request.dueTime() != null) template.setDueTime(LocalTime.parse(request.dueTime()));
        if (request.daysUntilDue() != null) template.setDaysUntilDue(request.daysUntilDue());
        if (request.nextOccurrence() != null) template.setNextOccurrence(LocalDate.parse(request.nextOccurrence()));
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

    public record CreateRecurringTemplateRequest(
        String title,
        String description,
        String defaultPriority,
        String defaultAssigneeId,
        String organization,
        List<String> tags,
        String recurrencePattern,
        Integer recurrenceDay,
        String dueTime,
        Integer daysUntilDue,
        String nextOccurrence,
        Boolean isActive
    ) {}
}
