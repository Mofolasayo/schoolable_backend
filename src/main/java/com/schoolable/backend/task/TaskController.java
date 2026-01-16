package com.schoolable.backend.task;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.websocket.WebSocketMessageController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping({"/api/tasks", "/tasks"})
@Tag(name = "Tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskRepository taskRepository;
    private final TaskSubtaskRepository subtaskRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final ProfileRepository profileRepository;
    private final WebSocketMessageController webSocketController;

    public TaskController(
            TaskRepository taskRepository,
            TaskSubtaskRepository subtaskRepository,
            TaskCommentRepository commentRepository,
            TaskAttachmentRepository attachmentRepository,
            TaskAssigneeRepository taskAssigneeRepository,
            ProfileRepository profileRepository,
            WebSocketMessageController webSocketController) {
        this.taskRepository = taskRepository;
        this.subtaskRepository = subtaskRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.profileRepository = profileRepository;
        this.webSocketController = webSocketController;
    }

    @Operation(summary = "Get all tasks with related data")
    @GetMapping
    public ResponseEntity<?> getAllTasks(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String query) {

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        UUID assignee = null;
        if (assigneeId != null) {
            try {
                assignee = UUID.fromString(assigneeId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid assigneeId"));
            }
        }
        String normalizedStatus = normalizeStatus(status);

        List<Long> assignedTaskIds = assignee != null
            ? taskAssigneeRepository.findByUserIdAndIsActiveTrue(assignee).stream()
                .map(TaskAssignee::getTaskId)
                .distinct()
                .toList()
            : List.of();

        var spec = TaskSpecifications.hasAssigneeOrTaskIds(assignee, assignedTaskIds)
            .and(TaskSpecifications.hasDepartment(department))
            .and(TaskSpecifications.hasStatus(normalizedStatus))
            .and(TaskSpecifications.hasPriority(priority))
            .and(TaskSpecifications.titleContains(query));

        Page<Task> pageData = taskRepository.findAll(
            spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Task> tasks = pageData.getContent();

        List<Map<String, Object>> result = tasks.stream()
            .map(this::buildTaskResponse)
            .toList();

        return ResponseEntity.ok(Map.of(
            "items", result,
            "page", page,
            "size", size,
            "total", pageData.getTotalElements()
        ));
    }

    @Operation(summary = "Get tasks assigned to the current user")
    @GetMapping("/assigned")
    public ResponseEntity<?> getMyTasks(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String query) {

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        String normalizedStatus = normalizeStatus(status);

        List<Long> assignedTaskIds = taskAssigneeRepository
            .findByUserIdAndIsActiveTrue(userId)
            .stream()
            .map(TaskAssignee::getTaskId)
            .distinct()
            .toList();

        var spec = TaskSpecifications.hasAssigneeOrTaskIds(userId, assignedTaskIds)
            .and(TaskSpecifications.hasStatus(normalizedStatus))
            .and(TaskSpecifications.hasPriority(priority))
            .and(TaskSpecifications.titleContains(query));

        Page<Task> pageData = taskRepository.findAll(
            spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Map<String, Object>> result = pageData.getContent().stream()
            .map(this::buildTaskResponse)
            .toList();

        return ResponseEntity.ok(Map.of(
            "items", result,
            "page", page,
            "size", size,
            "total", pageData.getTotalElements()
        ));
    }

    @Operation(summary = "Get tasks for the current user's team (department)")
    @GetMapping("/team")
    public ResponseEntity<?> getTeamTasks(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String query) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }
        Profile profile = profileOpt.get();
        if (!isAdmin(auth) && !Boolean.TRUE.equals(profile.getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Team lead access required"));
        }
        
        String department = profile.getDepartment();
        if (department == null || department.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User does not belong to a department"));
        }

        String normalizedStatus = normalizeStatus(status);
        var spec = TaskSpecifications.hasDepartment(department)
            .and(TaskSpecifications.hasStatus(normalizedStatus))
            .and(TaskSpecifications.hasPriority(priority))
            .and(TaskSpecifications.titleContains(query));

        Page<Task> pageData = taskRepository.findAll(
            spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Map<String, Object>> result = pageData.getContent().stream()
            .map(this::buildTaskResponse)
            .toList();

        return ResponseEntity.ok(Map.of(
            "items", result,
            "page", page,
            "size", size,
            "total", pageData.getTotalElements()
        ));
    }

    @Operation(summary = "Get a single task with all details")
    @GetMapping("/{id}")
    public ResponseEntity<?> getTask(@PathVariable Long id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();
        if (!canViewTask(userId, auth, task)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(buildTaskResponse(task));
    }

    @Operation(summary = "Create a new task")
    @PostMapping
    @Transactional
    public ResponseEntity<?> createTask(@RequestBody CreateTaskRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Profile creatorProfile = profileRepository.findById(userId).orElse(null);
        if (!isAdmin(auth) && (creatorProfile == null || !Boolean.TRUE.equals(creatorProfile.getIsTeamLead()))) {
            return ResponseEntity.status(403).body(Map.of("error", "Team lead or admin access required"));
        }

        String department = req.organization();
        if (!isAdmin(auth) && creatorProfile != null) {
            if (department == null || department.isBlank()) {
                department = creatorProfile.getDepartment();
            } else if (!department.equals(creatorProfile.getDepartment())) {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot create tasks outside your department"));
            }
        }

        List<UUID> assigneeIds;
        try {
            assigneeIds = parseAssigneeIds(req.assigneeIds(), req.assigneeId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid assigneeId"));
        }

        Task task = new Task();
        task.setTitle(req.title());
        task.setDescription(req.description());
        task.setAssigneeId(assigneeIds.isEmpty() ? null : assigneeIds.get(0));
        task.setOrganization(department);
        task.setPriority(req.priority() != null ? req.priority() : "Medium");
        task.setStatus(Task.TaskStatus.TODO.name());
        task.setDueDate(req.dueDate() != null ? OffsetDateTime.parse(req.dueDate()) : null);
        task.setDueTime(req.dueTime() != null ? java.time.LocalTime.parse(req.dueTime()) : null);
        task.setTags(req.tags() != null ? req.tags().toArray(new String[0]) : new String[0]);
        task.setProgress(0);
        task.setCreatedBy(userId);
        task.setCreatedAt(OffsetDateTime.now());
        if (req.blockedById() != null) {
            taskRepository.findById(req.blockedById()).ifPresent(task::setBlockedBy);
        }
        if (req.recurringTemplateId() != null) {
            task.setRecurringTemplateId(UUID.fromString(req.recurringTemplateId()));
        }
        if (req.isRecurringInstance() != null) {
            task.setIsRecurringInstance(req.isRecurringInstance());
        }

        task = taskRepository.save(task);

        if (!assigneeIds.isEmpty()) {
            syncTaskAssignees(task.getId(), assigneeIds, userId);
        }

        // Create subtasks
        if (req.subtasks() != null && !req.subtasks().isEmpty()) {
            for (var subtaskReq : req.subtasks()) {
                TaskSubtask subtask = new TaskSubtask();
                subtask.setTaskId(task.getId());
                subtask.setTitle(subtaskReq.title());
                subtask.setCompleted(false);
                subtask.setCreatedAt(OffsetDateTime.now());
                subtaskRepository.save(subtask);
            }
        }

        // Create attachments
        if (req.attachments() != null && !req.attachments().isEmpty()) {
            for (var attachReq : req.attachments()) {
                TaskAttachment attachment = new TaskAttachment();
                attachment.setTaskId(task.getId());
                attachment.setFileName(attachReq.name());
                attachment.setFileSize(attachReq.size());
                attachment.setFileType(attachReq.type());
                attachment.setFileUrl(attachReq.url());
                attachment.setFilePath(attachReq.path());
                attachment.setCreatedAt(OffsetDateTime.now());
                attachmentRepository.save(attachment);
            }
        }

        // Broadcast task creation via WebSocket
        Map<String, Object> taskResponse = buildTaskResponse(task);
        webSocketController.broadcastTaskUpdate("created", task.getId(), taskResponse);

        return ResponseEntity.ok(taskResponse);
    }

    @Operation(summary = "Update a task")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestBody UpdateTaskRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();
        if (!canUpdateTaskProgress(userId, auth, task)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        if (req.title() != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        List<UUID> assigneeIdsToSync = null;
        if (req.assigneeIds() != null || req.assigneeId() != null) {
            try {
                assigneeIdsToSync = parseAssigneeIds(req.assigneeIds(), req.assigneeId());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid assigneeId"));
            }
            task.setAssigneeId(assigneeIdsToSync.isEmpty() ? null : assigneeIdsToSync.get(0));
        }
        if (req.organization() != null) task.setOrganization(req.organization());
        if (req.priority() != null) task.setPriority(req.priority());
        if (req.status() != null) {
            String nextStatus = normalizeStatus(req.status());
            if (!isValidTransition(task.getStatus(), nextStatus)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status transition"));
            }
            if (task.isBlocked() && (Task.TaskStatus.IN_PROGRESS.name().equals(nextStatus)
                || Task.TaskStatus.REVIEW.name().equals(nextStatus)
                || Task.TaskStatus.DONE.name().equals(nextStatus))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Task is blocked by another task"));
            }
            task.setStatus(nextStatus);
        }
        if (req.dueDate() != null) task.setDueDate(OffsetDateTime.parse(req.dueDate()));
        if (req.dueTime() != null) task.setDueTime(java.time.LocalTime.parse(req.dueTime()));
        if (req.tags() != null) task.setTags(req.tags().toArray(new String[0]));
        if (req.progress() != null) task.setProgress(req.progress());
        if (req.blockedById() != null) {
            if (req.blockedById() == 0) {
                task.setBlockedBy(null);
            } else {
                taskRepository.findById(req.blockedById()).ifPresent(task::setBlockedBy);
            }
        }
        if (req.recurringTemplateId() != null) {
            task.setRecurringTemplateId(UUID.fromString(req.recurringTemplateId()));
        }
        if (req.isRecurringInstance() != null) {
            task.setIsRecurringInstance(req.isRecurringInstance());
        }

        taskRepository.save(task);
        if (assigneeIdsToSync != null) {
            syncTaskAssignees(task.getId(), assigneeIdsToSync, userId);
        }
        
        // Broadcast task update via WebSocket
        Map<String, Object> taskResponse = buildTaskResponse(task);
        webSocketController.broadcastTaskUpdate("updated", task.getId(), taskResponse);
        
        return ResponseEntity.ok(taskResponse);
    }

    @Operation(summary = "Update task status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateTaskStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();
        if (!canUpdateTaskProgress(userId, auth, task)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        String oldStatus = task.getStatus();
        String nextStatus = normalizeStatus(req.status());
        if (!isValidTransition(oldStatus, nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status transition"));
        }
        if (task.isBlocked() && (Task.TaskStatus.IN_PROGRESS.name().equals(nextStatus)
            || Task.TaskStatus.REVIEW.name().equals(nextStatus)
            || Task.TaskStatus.DONE.name().equals(nextStatus))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task is blocked by another task"));
        }
        task.setStatus(nextStatus);
        if (req.progress() != null) {
            task.setProgress(req.progress());
        } else {
            // Auto-set progress based on status
            if (Task.TaskStatus.DONE.name().equals(nextStatus)) {
                task.setProgress(100);
            } else if (Task.TaskStatus.TODO.name().equals(nextStatus)) {
                task.setProgress(0);
            }
        }
        
        // Set rating_pending when task is marked as completed
        // This triggers the rating popup for the task creator
        if (Task.TaskStatus.DONE.name().equals(nextStatus) && !Task.TaskStatus.DONE.name().equals(normalizeStatus(oldStatus))) {
            if (task.getCreatedBy() != null && task.getAssigneeId() != null 
                && !task.getCreatedBy().equals(task.getAssigneeId())) {
                // Only prompt for rating if creator is different from assignee
                task.setRatingPending(true);
            }
        }

        // Track first response time - when assignee first updates the task
        if (task.getFirstResponseAt() == null && 
            task.getAssigneeId() != null && 
            task.getAssigneeId().equals(userId)) {
            task.setFirstResponseAt(OffsetDateTime.now());
        }

        taskRepository.save(task);
        
        // Broadcast task status update via WebSocket
        Map<String, Object> taskResponse = buildTaskResponse(task);
        webSocketController.broadcastTaskUpdate("updated", task.getId(), taskResponse);
        
        return ResponseEntity.ok(taskResponse);
    }

    @Operation(summary = "Update task description")
    @PatchMapping("/{id}/description")
    public ResponseEntity<?> updateTaskDescription(@PathVariable Long id, @RequestBody UpdateDescriptionRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();
        UUID userId = (UUID) auth.getPrincipal();
        if (!canManageTask(userId, auth, task)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        task.setDescription(req.description());
        taskRepository.save(task);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Delete a task")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteTask(@PathVariable Long id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        if (!canUpdateTaskProgress(userId, auth, taskOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        // Delete related records first (cascading should handle this via FK, but being explicit)
        subtaskRepository.deleteByTaskId(id);
        commentRepository.deleteByTaskId(id);
        attachmentRepository.deleteByTaskId(id);
        taskRepository.deleteById(id);

        // Broadcast task deletion via WebSocket
        webSocketController.broadcastTaskUpdate("deleted", id, Map.of("id", id));

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ==================== SUBTASKS ====================

    @Operation(summary = "Add a subtask to a task")
    @PostMapping("/{taskId}/subtasks")
    public ResponseEntity<?> addSubtask(@PathVariable Long taskId, @RequestBody AddSubtaskRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        if (!canUpdateTaskProgress(userId, auth, taskOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        TaskSubtask subtask = new TaskSubtask();
        subtask.setTaskId(taskId);
        subtask.setTitle(req.title());
        subtask.setCompleted(false);
        subtask.setCreatedAt(OffsetDateTime.now());
        subtaskRepository.save(subtask);

        recalculateTaskProgress(taskId);

        return ResponseEntity.ok(Map.of(
                "id", subtask.getId(),
                "title", subtask.getTitle(),
                "completed", subtask.getCompleted()
        ));
    }

    @Operation(summary = "Update subtask completion status")
    @PatchMapping("/subtasks/{subtaskId}")
    public ResponseEntity<?> updateSubtask(@PathVariable Long subtaskId, @RequestBody UpdateSubtaskRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        var subtaskOpt = subtaskRepository.findById(subtaskId);
        if (subtaskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Subtask not found"));
        }

        TaskSubtask subtask = subtaskOpt.get();
        var taskOpt = taskRepository.findById(subtask.getTaskId());
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        if (!canManageTask(userId, auth, taskOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
        subtask.setCompleted(req.completed());
        subtaskRepository.save(subtask);

        recalculateTaskProgress(subtask.getTaskId());

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ==================== COMMENTS ====================

    @Operation(summary = "Add a comment to a task")
    @PostMapping("/{taskId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long taskId, @RequestBody AddCommentRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }
        if (!canViewTask(userId, auth, taskOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setUserId(userId);
        comment.setContent(req.content());
        comment.setCreatedAt(OffsetDateTime.now());
        commentRepository.save(comment);

        // Get author profile for response
        var profile = profileRepository.findById(userId).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("id", comment.getId());
        response.put("content", comment.getContent());
        response.put("created_at", comment.getCreatedAt());
        if (profile != null) {
            response.put("author", buildProfileSummary(profile));
        }

        return ResponseEntity.ok(response);
    }

    // ==================== ATTACHMENTS ====================

    @Operation(summary = "Add an attachment to a task")
    @PostMapping("/{taskId}/attachments")
    public ResponseEntity<?> addAttachment(@PathVariable Long taskId, @RequestBody AddAttachmentRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }
        UUID userId = (UUID) auth.getPrincipal();
        if (!canViewTask(userId, auth, taskOpt.get())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTaskId(taskId);
        attachment.setFileName(req.name());
        attachment.setFileSize(req.size());
        attachment.setFileType(req.type());
        attachment.setFileUrl(req.url());
        attachment.setFilePath(req.path());
        attachment.setCreatedAt(OffsetDateTime.now());
        attachmentRepository.save(attachment);

        return ResponseEntity.ok(Map.of(
                "id", attachment.getId(),
                "name", attachment.getFileName(),
                "size", attachment.getFileSize(),
                "type", attachment.getFileType(),
                "url", attachment.getFileUrl()
        ));
    }

    // ==================== TASK QUALITY RATING ====================

    public record TaskRatingRequest(
        Integer rating,  // 1-5 stars
        String comment   // Optional feedback
    ) {}

    @Operation(summary = "Get tasks pending quality rating")
    @GetMapping("/rating/pending")
    public ResponseEntity<?> getTasksPendingRating(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        List<Task> pendingTasks = taskRepository.findByCreatedByAndRatingPendingTrue(userId);

        List<Map<String, Object>> result = pendingTasks.stream()
                .map(task -> {
                    Map<String, Object> taskInfo = new HashMap<>();
                    taskInfo.put("id", task.getId());
                    taskInfo.put("title", task.getTitle());
                    taskInfo.put("assigneeId", task.getAssigneeId());
                    taskInfo.put("completedAt", task.getUpdatedAt());
                    
                    // Get assignee name
                    if (task.getAssigneeId() != null) {
                        profileRepository.findById(task.getAssigneeId()).ifPresent(profile -> 
                            taskInfo.put("assigneeName", profile.getFullName())
                        );
                    }
                    return taskInfo;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
            "pendingRatings", result,
            "count", result.size()
        ));
    }

    @Operation(summary = "Rate a completed task")
    @PostMapping("/{taskId}/rate")
    @Transactional
    public ResponseEntity<?> rateTask(
            Authentication auth,
            @PathVariable Long taskId,
            @RequestBody TaskRatingRequest request) {

        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();

        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();

        // Only the creator can rate the task
        if (!userId.equals(task.getCreatedBy())) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Only the task creator can rate this task"
            ));
        }

        // Validate rating
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Rating must be between 1 and 5"
            ));
        }

        // Save rating
        task.setQualityRating(request.rating());
        task.setRatedBy(userId);
        task.setRatedAt(OffsetDateTime.now());
        task.setRatingComment(request.comment());
        task.setRatingPending(false);
        taskRepository.save(task);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Task rated successfully",
            "taskId", taskId,
            "rating", request.rating()
        ));
    }

    @Operation(summary = "Get average quality rating for an employee")
    @GetMapping("/rating/average/{employeeId}")
    public ResponseEntity<?> getAverageRating(
            Authentication auth,
            @PathVariable UUID employeeId) {

        Double avgRating = taskRepository.getAverageQualityRating(employeeId);

        return ResponseEntity.ok(Map.of(
            "employeeId", employeeId,
            "averageRating", avgRating != null ? Math.round(avgRating * 10) / 10.0 : null,
            "hasRatings", avgRating != null
        ));
    }

    // ==================== HELPER METHODS ====================


    private void recalculateTaskProgress(Long taskId) {
        List<TaskSubtask> subtasks = subtaskRepository.findByTaskIdOrderByIdAsc(taskId);
        if (subtasks.isEmpty()) return;

        long completed = subtasks.stream().filter(s -> Boolean.TRUE.equals(s.getCompleted())).count();
        int progress = (int) Math.round((double) completed / subtasks.size() * 100);

        var taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setProgress(progress);
            if (progress == 100) {
                task.setStatus(Task.TaskStatus.DONE.name());
            } else if (progress > 0) {
                task.setStatus(Task.TaskStatus.IN_PROGRESS.name());
            }
            taskRepository.save(task);
        }
    }

    private List<UUID> parseAssigneeIds(List<String> assigneeIds, String assigneeId) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (assigneeIds != null) {
            for (String id : assigneeIds) {
                if (id != null && !id.isBlank()) {
                    uniqueIds.add(id.trim());
                }
            }
        }
        if (assigneeId != null && !assigneeId.isBlank()) {
            uniqueIds.add(assigneeId.trim());
        }
        List<UUID> parsed = new ArrayList<>();
        for (String id : uniqueIds) {
            parsed.add(UUID.fromString(id));
        }
        return parsed;
    }

    private void syncTaskAssignees(Long taskId, List<UUID> assigneeIds, UUID assignedBy) {
        List<TaskAssignee> activeAssignments = taskAssigneeRepository.findByTaskIdAndIsActiveTrue(taskId);
        Map<UUID, TaskAssignee> activeByUser = new HashMap<>();
        for (TaskAssignee assignment : activeAssignments) {
            activeByUser.put(assignment.getUserId(), assignment);
        }

        Set<UUID> desired = new LinkedHashSet<>(assigneeIds);
        List<TaskAssignee> updates = new ArrayList<>();

        for (TaskAssignee assignment : activeAssignments) {
            if (!desired.contains(assignment.getUserId())) {
                assignment.setIsActive(false);
                updates.add(assignment);
            }
        }

        for (UUID assignee : desired) {
            TaskAssignee assignment = activeByUser.get(assignee);
            String role = "assignee";
            if (assignment == null) {
                assignment = new TaskAssignee(taskId, assignee, role, assignedBy);
            } else {
                assignment.setRole(role);
                assignment.setIsActive(true);
            }
            updates.add(assignment);
        }

        if (!updates.isEmpty()) {
            taskAssigneeRepository.saveAll(updates);
        }
    }

    private Map<String, Object> buildTaskResponse(Task task) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", task.getId());
        response.put("title", task.getTitle());
        response.put("description", task.getDescription());
        response.put("assignee_id", task.getAssigneeId());
        response.put("organization", task.getOrganization());
        response.put("priority", task.getPriority());
        response.put("status", normalizeStatus(task.getStatus()));
        response.put("due_date", task.getDueDate());
        response.put("due_time", task.getDueTime() != null ? task.getDueTime().toString() : null);
        response.put("tags", task.getTags() != null ? Arrays.asList(task.getTags()) : List.of());
        response.put("progress", task.getProgress());
        response.put("created_by", task.getCreatedBy());
        response.put("created_at", task.getCreatedAt());
        response.put("updated_at", task.getUpdatedAt());
        response.put("blocked_by_id", task.getBlockedById());
        response.put("is_blocked", task.isBlocked());
        response.put("recurring_template_id", task.getRecurringTemplateId());
        response.put("is_recurring_instance", task.getIsRecurringInstance());
        
        // Quality Rating fields
        response.put("quality_rating", task.getQualityRating());
        response.put("rated_by", task.getRatedBy());
        response.put("rated_at", task.getRatedAt());
        response.put("rating_comment", task.getRatingComment());
        response.put("rating_pending", task.getRatingPending());

        // Get assignee profile
        if (task.getAssigneeId() != null) {
            var profileOpt = profileRepository.findById(task.getAssigneeId());
            profileOpt.ifPresent(profile -> response.put("assignee", buildProfileSummary(profile)));
        }

        List<TaskAssignee> assignees = taskAssigneeRepository.findByTaskIdAndIsActiveTrue(task.getId());
        List<Map<String, Object>> assigneeSummaries = new ArrayList<>();
        for (TaskAssignee assignment : assignees) {
            Map<String, Object> summary = new HashMap<>();
            Profile profile = profileRepository.findById(assignment.getUserId()).orElse(null);
            if (profile != null) {
                summary.putAll(buildProfileSummary(profile));
            } else {
                summary.put("id", assignment.getUserId());
            }
            summary.put("role", "assignee");
            assigneeSummaries.add(summary);
        }
        response.put("assignees", assigneeSummaries);

        // Get subtasks
        List<TaskSubtask> subtasks = subtaskRepository.findByTaskIdOrderByIdAsc(task.getId());
        response.put("subtasks", subtasks.stream().map(s -> Map.of(
                "id", s.getId(),
                "title", s.getTitle(),
                "completed", s.getCompleted() != null ? s.getCompleted() : false
        )).toList());

        // Get comments with author info
        List<TaskComment> comments = commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId());
        response.put("comments", comments.stream().map(c -> {
            Map<String, Object> commentMap = new HashMap<>();
            commentMap.put("id", c.getId());
            commentMap.put("content", c.getContent());
            commentMap.put("created_at", c.getCreatedAt());
            if (c.getUserId() != null) {
                var authorOpt = profileRepository.findById(c.getUserId());
                authorOpt.ifPresent(author -> commentMap.put("author", buildProfileSummary(author)));
            }
            return commentMap;
        }).toList());

        // Get attachments
        List<TaskAttachment> attachments = attachmentRepository.findByTaskIdOrderByIdAsc(task.getId());
        response.put("attachments", attachments.stream().map(a -> Map.of(
                "id", a.getId(),
                "file_name", a.getFileName() != null ? a.getFileName() : "",
                "file_size", a.getFileSize() != null ? a.getFileSize() : "",
                "file_type", a.getFileType() != null ? a.getFileType() : "",
                "file_url", a.getFileUrl() != null ? a.getFileUrl() : ""
        )).toList());

        return response;
    }

    private Map<String, Object> buildProfileSummary(Profile p) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", p.getId());
        summary.put("full_name", p.getFullName());
        summary.put("department", p.getDepartment());
        summary.put("gender", p.getGender());
        summary.put("email", p.getEmail());
        summary.put("employee_id", p.getEmployeeId());

        // Generate avatar URL if not set
        String avatarUrl = p.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            String style = "bottts";
            if (p.getGender() != null) {
                if (p.getGender().equalsIgnoreCase("male")) style = "adventurer";
                else if (p.getGender().equalsIgnoreCase("female")) style = "adventurer-neutral";
            }
            String seed = p.getEmployeeId();
            if (seed == null || seed.isEmpty()) seed = p.getEmail();
            if (seed == null || seed.isEmpty()) seed = p.getFullName();
            if (seed == null || seed.isEmpty()) seed = "User";
            avatarUrl = "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
        }
        summary.put("avatar_url", avatarUrl);

        return summary;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
        return switch (normalized) {
            case "PENDING", "TODO" -> Task.TaskStatus.TODO.name();
            case "IN_PROGRESS", "IN-PROGRESS" -> Task.TaskStatus.IN_PROGRESS.name();
            case "REVIEW" -> Task.TaskStatus.REVIEW.name();
            case "COMPLETED", "DONE" -> Task.TaskStatus.DONE.name();
            case "CANCELLED", "CANCELED" -> Task.TaskStatus.CANCELLED.name();
            default -> normalized;
        };
    }

    private boolean isValidTransition(String currentStatus, String nextStatus) {
        if (nextStatus == null) return true;
        Task.TaskStatus current = parseStatus(currentStatus);
        Task.TaskStatus next = parseStatus(nextStatus);
        if (next == null) return false;
        if (current == null || current == next) return true;

        return switch (current) {
            case TODO -> next == Task.TaskStatus.IN_PROGRESS || next == Task.TaskStatus.DONE || next == Task.TaskStatus.CANCELLED;
            case IN_PROGRESS -> next == Task.TaskStatus.REVIEW || next == Task.TaskStatus.DONE || next == Task.TaskStatus.CANCELLED;
            case REVIEW -> next == Task.TaskStatus.DONE || next == Task.TaskStatus.IN_PROGRESS || next == Task.TaskStatus.CANCELLED;
            case DONE -> next == Task.TaskStatus.IN_PROGRESS;
            case CANCELLED -> next == Task.TaskStatus.TODO;
        };
    }

    private Task.TaskStatus parseStatus(String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) return null;
        try {
            return Task.TaskStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean canViewTask(UUID userId, Authentication auth, Task task) {
        if (isAdmin(auth)) return true;
        if (userId.equals(task.getAssigneeId()) || userId.equals(task.getCreatedBy())) {
            return true;
        }
        if (taskAssigneeRepository.existsByTaskIdAndUserIdAndIsActiveTrue(task.getId(), userId)) {
            return true;
        }
        Profile profile = profileRepository.findById(userId).orElse(null);
        return profile != null && Boolean.TRUE.equals(profile.getIsTeamLead()) &&
            profile.getDepartment() != null && profile.getDepartment().equals(task.getOrganization());
    }

    private boolean canManageTask(UUID userId, Authentication auth, Task task) {
        if (isAdmin(auth)) return true;
        if (userId.equals(task.getCreatedBy())) return true;
        Profile profile = profileRepository.findById(userId).orElse(null);
        return profile != null && Boolean.TRUE.equals(profile.getIsTeamLead()) &&
            profile.getDepartment() != null && profile.getDepartment().equals(task.getOrganization());
    }

    private boolean canUpdateTaskProgress(UUID userId, Authentication auth, Task task) {
        if (canManageTask(userId, auth, task)) return true;
        if (userId.equals(task.getAssigneeId())) return true;
        return taskAssigneeRepository.existsByTaskIdAndUserIdAndIsActiveTrue(task.getId(), userId);
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    // ==================== REQUEST RECORDS ====================

    public record CreateTaskRequest(
            String title,
            String description,
            String assigneeId,
            List<String> assigneeIds,
            String organization,
            String priority,
            String dueDate,
            String dueTime, // HH:mm format
            List<String> tags,
            Long blockedById,
            String recurringTemplateId,
            Boolean isRecurringInstance,
            List<SubtaskRequest> subtasks,
            List<AttachmentRequest> attachments
    ) {}

    public record SubtaskRequest(String title) {}

    public record AttachmentRequest(String name, String size, String type, String url, String path) {}

    public record UpdateTaskRequest(
            String title,
            String description,
            String assigneeId,
            List<String> assigneeIds,
            String organization,
            String priority,
            String status,
            String dueDate,
            String dueTime, // HH:mm format
            List<String> tags,
            Integer progress,
            Long blockedById,
            String recurringTemplateId,
            Boolean isRecurringInstance
    ) {}

    public record UpdateStatusRequest(String status, Integer progress) {}

    public record UpdateDescriptionRequest(String description) {}

    public record AddSubtaskRequest(String title) {}

    public record UpdateSubtaskRequest(Boolean completed) {}

    public record AddCommentRequest(String content) {}

    public record AddAttachmentRequest(String name, String size, String type, String url, String path) {}
}
