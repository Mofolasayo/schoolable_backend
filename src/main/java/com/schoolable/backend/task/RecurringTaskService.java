package com.schoolable.backend.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RecurringTaskService {

    private final RecurringTaskTemplateRepository templateRepository;
    private final TaskRepository taskRepository;

    public RecurringTaskService(RecurringTaskTemplateRepository templateRepository, TaskRepository taskRepository) {
        this.templateRepository = templateRepository;
        this.taskRepository = taskRepository;
    }

    @Scheduled(cron = "0 5 1 * * *")
    @Transactional
    public void createRecurringTasks() {
        LocalDate today = LocalDate.now();
        List<RecurringTaskTemplate> templates = templateRepository.findByIsActiveTrueAndNextOccurrenceLessThanEqual(today);

        for (RecurringTaskTemplate template : templates) {
            if (template.getNextOccurrence() == null) {
                template.setNextOccurrence(template.computeNextOccurrence(today, true));
                templateRepository.save(template);
                continue;
            }

            if (wasCreatedToday(template, today)) {
                template.setNextOccurrence(template.computeNextOccurrence(today, false));
                templateRepository.save(template);
                continue;
            }

            Task task = new Task();
            task.setTitle(template.getTitle());
            task.setDescription(template.getDescription());
            task.setPriority(template.getDefaultPriority());
            task.setAssigneeId(template.getDefaultAssigneeId());
            task.setOrganization(template.getOrganization());
            task.setTags(template.getTags());
            task.setStatus(Task.TaskStatus.TODO.name());
            task.setCreatedBy(template.getCreatedBy());
            task.setCreatedAt(OffsetDateTime.now());
            task.setRecurringTemplateId(template.getId());
            task.setIsRecurringInstance(true);

            if (template.getDaysUntilDue() != null) {
                task.setDueDate(OffsetDateTime.now().plusDays(template.getDaysUntilDue()));
            }
            task.setDueTime(template.getDueTime());

            taskRepository.save(task);

            template.setLastCreatedAt(LocalDateTime.now());
            template.setNextOccurrence(template.computeNextOccurrence(today, false));
            templateRepository.save(template);
        }
    }

    private boolean wasCreatedToday(RecurringTaskTemplate template, LocalDate today) {
        LocalDateTime lastCreatedAt = template.getLastCreatedAt();
        return lastCreatedAt != null && lastCreatedAt.toLocalDate().isEqual(today);
    }
}
