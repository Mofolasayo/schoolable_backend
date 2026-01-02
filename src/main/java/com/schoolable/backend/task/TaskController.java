package com.schoolable.backend.task;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.websocket.WebSocketMessageController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final TaskSubtaskRepository subtaskRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final ProfileRepository profileRepository;
    private final WebSocketMessageController webSocketController;

    public TaskController(
            TaskRepository taskRepository,
            TaskSubtaskRepository subtaskRepository,
            TaskCommentRepository commentRepository,
            TaskAttachmentRepository attachmentRepository,
            ProfileRepository profileRepository,
            WebSocketMessageController webSocketController) {
        this.taskRepository = taskRepository;
        this.subtaskRepository = subtaskRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.profileRepository = profileRepository;
        this.webSocketController = webSocketController;
    }

    @Operation(summary = "Get all tasks with related data")
    @GetMapping
    public ResponseEntity<?> getAllTasks(Authentication auth) {
        System.out.println("🤖 TaskController.getAllTasks reached");
        if (auth == null) {
            System.out.println("   ❌ Auth is NULL");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        System.out.println("   ✅ Auth principal: " + auth.getPrincipal());
        
        if (auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        List<Task> tasks = taskRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = tasks.stream()
                .map(this::buildTaskResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get tasks assigned to the current user")

    @GetMapping("/assigned")
    public ResponseEntity<?> getMyTasks(Authentication auth) {
        System.out.println("🤖 TaskController.getMyTasks reached");
        try {
            if (auth == null) {
                System.out.println("   ❌ Auth is NULL");
                return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated (Auth is null)"));
            }
            if (auth.getPrincipal() == null) {
                System.out.println("   ❌ Principal is null");
                return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated (Principal is null)"));
            }
            
            UUID userId = (UUID) auth.getPrincipal();
            System.out.println("   Fetching tasks for user: " + userId);

            List<Task> tasks = taskRepository.findByAssigneeIdOrderByCreatedAtDesc(userId);
            System.out.println("   ✅ Tasks found: " + tasks.size());
            
            List<Map<String, Object>> result = tasks.stream()
                    .map(this::buildTaskResponse)
                    .toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.out.println("   ❌ ERROR in getMyTasks: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal Error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get tasks for the current user's team (department)")
    @GetMapping("/team")
    public ResponseEntity<?> getTeamTasks(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        var profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }
        
        String department = profileOpt.get().getDepartment();
        if (department == null || department.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User does not belong to a department"));
        }

        List<Task> tasks = taskRepository.findByOrganizationOrderByCreatedAtDesc(department);
        List<Map<String, Object>> result = tasks.stream()
                .map(this::buildTaskResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get a single task with all details")
    @GetMapping("/{id}")
    public ResponseEntity<?> getTask(@PathVariable Long id, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        return ResponseEntity.ok(buildTaskResponse(taskOpt.get()));
    }

    @Operation(summary = "Create a new task")
    @PostMapping
    @Transactional
    public ResponseEntity<?> createTask(@RequestBody CreateTaskRequest req, Authentication auth) {
        System.out.println("🤖 TaskController.createTask reached");
        if (auth == null || auth.getPrincipal() == null) {
            System.out.println("   ❌ Auth/Principal is NULL");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Task task = new Task();
        task.setTitle(req.title());
        task.setDescription(req.description());
        task.setAssigneeId(req.assigneeId() != null ? UUID.fromString(req.assigneeId()) : null);
        task.setOrganization(req.organization());
        task.setPriority(req.priority() != null ? req.priority() : "Medium");
        task.setStatus("Pending");
        task.setDueDate(req.dueDate() != null ? OffsetDateTime.parse(req.dueDate()) : null);
        task.setDueTime(req.dueTime() != null ? java.time.LocalTime.parse(req.dueTime()) : null);
        task.setTags(req.tags() != null ? req.tags().toArray(new String[0]) : new String[0]);
        task.setProgress(0);
        task.setCreatedBy(userId);
        task.setCreatedAt(OffsetDateTime.now());

        task = taskRepository.save(task);

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

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();
        if (req.title() != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.assigneeId() != null) task.setAssigneeId(UUID.fromString(req.assigneeId()));
        if (req.organization() != null) task.setOrganization(req.organization());
        if (req.priority() != null) task.setPriority(req.priority());
        if (req.status() != null) task.setStatus(req.status());
        if (req.dueDate() != null) task.setDueDate(OffsetDateTime.parse(req.dueDate()));
        if (req.tags() != null) task.setTags(req.tags().toArray(new String[0]));
        if (req.progress() != null) task.setProgress(req.progress());

        taskRepository.save(task);
        
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

        var taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
        }

        Task task = taskOpt.get();
        String oldStatus = task.getStatus();
        task.setStatus(req.status());
        if (req.progress() != null) {
            task.setProgress(req.progress());
        } else {
            // Auto-set progress based on status
            if ("Completed".equals(req.status())) {
                task.setProgress(100);
            } else if ("Pending".equals(req.status())) {
                task.setProgress(0);
            }
        }
        
        // Set rating_pending when task is marked as completed
        // This triggers the rating popup for the task creator
        if ("Completed".equals(req.status()) && !"Completed".equals(oldStatus)) {
            if (task.getCreatedBy() != null && task.getAssigneeId() != null 
                && !task.getCreatedBy().equals(task.getAssigneeId())) {
                // Only prompt for rating if creator is different from assignee
                task.setRatingPending(true);
            }
        }

        // Track first response time - when assignee first updates the task
        UUID userId = (UUID) auth.getPrincipal();
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

        if (!taskRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
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

        if (!taskRepository.existsById(taskId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
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

        if (!taskRepository.existsById(taskId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
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

        if (!taskRepository.existsById(taskId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found"));
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
                            taskInfo.put("assigneeName", profile.getFirstName() + " " + profile.getLastName())
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
                task.setStatus("Completed");
            } else if (progress > 0) {
                task.setStatus("In Progress");
            }
            taskRepository.save(task);
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
        response.put("status", task.getStatus());
        response.put("due_date", task.getDueDate());
        response.put("due_time", task.getDueTime() != null ? task.getDueTime().toString() : null);
        response.put("tags", task.getTags() != null ? Arrays.asList(task.getTags()) : List.of());
        response.put("progress", task.getProgress());
        response.put("created_by", task.getCreatedBy());
        response.put("created_at", task.getCreatedAt());
        
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

    // ==================== REQUEST RECORDS ====================

    public record CreateTaskRequest(
            String title,
            String description,
            String assigneeId,
            String organization,
            String priority,
            String dueDate,
            String dueTime, // HH:mm format
            List<String> tags,
            List<SubtaskRequest> subtasks,
            List<AttachmentRequest> attachments
    ) {}

    public record SubtaskRequest(String title) {}

    public record AttachmentRequest(String name, String size, String type, String url, String path) {}

    public record UpdateTaskRequest(
            String title,
            String description,
            String assigneeId,
            String organization,
            String priority,
            String status,
            String dueDate,
            String dueTime, // HH:mm format
            List<String> tags,
            Integer progress
    ) {}

    public record UpdateStatusRequest(String status, Integer progress) {}

    public record UpdateDescriptionRequest(String description) {}

    public record AddSubtaskRequest(String title) {}

    public record UpdateSubtaskRequest(Boolean completed) {}

    public record AddCommentRequest(String content) {}

    public record AddAttachmentRequest(String name, String size, String type, String url, String path) {}
}
