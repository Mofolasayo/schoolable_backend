package com.schoolable.backend.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecurringTaskService {

    private final RecurringTaskTemplateRepository templateRepository;
    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskSubtaskRepository subtaskRepository;
    private final TaskAttachmentRepository attachmentRepository;

    public RecurringTaskService(
            RecurringTaskTemplateRepository templateRepository,
            TaskRepository taskRepository,
            TaskAssigneeRepository taskAssigneeRepository,
            TaskSubtaskRepository subtaskRepository,
            TaskAttachmentRepository attachmentRepository) {
        this.templateRepository = templateRepository;
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.subtaskRepository = subtaskRepository;
        this.attachmentRepository = attachmentRepository;
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

            Optional<Task> sourceTaskOpt = taskRepository
                .findTopByRecurringTemplateIdOrderByCreatedAtDesc(template.getId());
            Task sourceTask = sourceTaskOpt.orElse(null);
            List<UUID> assigneeIds = resolveAssigneeIds(template, sourceTask);

            Task task = new Task();
            task.setTitle(template.getTitle());
            task.setDescription(template.getDescription());
            task.setPriority(template.getDefaultPriority());
            task.setAssigneeId(assigneeIds.isEmpty() ? null : assigneeIds.get(0));
            task.setOrganization(template.getOrganization());
            task.setTags(template.getTags());
            task.setStatus(Task.TaskStatus.TODO.name());
            task.setCreatedBy(template.getCreatedBy());
            task.setCreatedAt(OffsetDateTime.now());
            task.setRecurringTemplateId(template.getId());
            task.setIsRecurringInstance(true);
            task.setProgress(0);

            if (template.getDaysUntilDue() != null) {
                task.setDueDate(OffsetDateTime.now().plusDays(template.getDaysUntilDue()));
            }
            task.setDueTime(template.getDueTime());

            Task savedTask = taskRepository.save(task);
            syncRecurringAssignees(savedTask.getId(), assigneeIds, template.getCreatedBy());
            copySubtasks(savedTask.getId(), sourceTask);
            copyAttachments(savedTask.getId(), sourceTask);

            template.setLastCreatedAt(LocalDateTime.now());
            template.setNextOccurrence(template.computeNextOccurrence(today, false));
            templateRepository.save(template);
        }
    }

    private boolean wasCreatedToday(RecurringTaskTemplate template, LocalDate today) {
        LocalDateTime lastCreatedAt = template.getLastCreatedAt();
        return lastCreatedAt != null && lastCreatedAt.toLocalDate().isEqual(today);
    }

    private List<UUID> resolveAssigneeIds(RecurringTaskTemplate template, Task sourceTask) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (sourceTask != null) {
            List<TaskAssignee> assignees =
                taskAssigneeRepository.findByTaskIdAndIsActiveTrue(sourceTask.getId());
            for (TaskAssignee assignee : assignees) {
                if (assignee.getUserId() != null) {
                    ids.add(assignee.getUserId());
                }
            }
            if (ids.isEmpty() && sourceTask.getAssigneeId() != null) {
                ids.add(sourceTask.getAssigneeId());
            }
        }
        if (ids.isEmpty() && template.getDefaultAssigneeId() != null) {
            ids.add(template.getDefaultAssigneeId());
        }
        return new ArrayList<>(ids);
    }

    private void syncRecurringAssignees(Long taskId, List<UUID> assigneeIds, UUID assignedBy) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return;
        List<TaskAssignee> entries = new ArrayList<>();
        for (UUID assigneeId : assigneeIds) {
            TaskAssignee assignment = new TaskAssignee(taskId, assigneeId, "assignee", assignedBy);
            entries.add(assignment);
        }
        taskAssigneeRepository.saveAll(entries);
    }

    private void copySubtasks(Long taskId, Task sourceTask) {
        if (sourceTask == null) return;
        List<TaskSubtask> subtasks = subtaskRepository.findByTaskIdOrderByIdAsc(sourceTask.getId());
        if (subtasks.isEmpty()) return;

        List<TaskSubtask> copies = new ArrayList<>();
        for (TaskSubtask subtask : subtasks) {
            TaskSubtask copy = new TaskSubtask();
            copy.setTaskId(taskId);
            copy.setTitle(subtask.getTitle());
            copy.setCompleted(false);
            copy.setCreatedAt(OffsetDateTime.now());
            copies.add(copy);
        }
        subtaskRepository.saveAll(copies);
    }

    private void copyAttachments(Long taskId, Task sourceTask) {
        if (sourceTask == null) return;
        List<TaskAttachment> attachments = attachmentRepository.findByTaskIdOrderByIdAsc(sourceTask.getId());
        if (attachments.isEmpty()) return;

        List<TaskAttachment> copies = new ArrayList<>();
        for (TaskAttachment attachment : attachments) {
            TaskAttachment copy = new TaskAttachment();
            copy.setTaskId(taskId);
            copy.setFileName(attachment.getFileName());
            copy.setFileSize(attachment.getFileSize());
            copy.setFileType(attachment.getFileType());
            copy.setFileUrl(attachment.getFileUrl());
            copy.setFilePath(attachment.getFilePath());
            copy.setCreatedAt(OffsetDateTime.now());
            copies.add(copy);
        }
        attachmentRepository.saveAll(copies);
    }
}
