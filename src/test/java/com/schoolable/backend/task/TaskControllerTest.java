package com.schoolable.backend.task;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.storage.StorageService;
import com.schoolable.backend.websocket.WebSocketMessageController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private TaskSubtaskRepository subtaskRepository;

    @MockBean
    private TaskCommentRepository commentRepository;

    @MockBean
    private TaskAttachmentRepository attachmentRepository;

    @MockBean
    private TaskAssigneeRepository taskAssigneeRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private WebSocketMessageController webSocketController;

    @MockBean
    private StorageService storageService;

    @MockBean
    private NotificationService notificationService;

    @Test
    void getAssignedTasks_requiresAuth() throws Exception {
        mockMvc.perform(get("/tasks/assigned"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthenticated")));
    }

    @Test
    void getAssignedTasks_returnsTasks() throws Exception {
        TaskAssignee assignment = new TaskAssignee(1L, USER_ID, "assignee", USER_ID);
        when(taskAssigneeRepository.findByUserIdAndIsActiveTrue(USER_ID)).thenReturn(List.of(assignment));

        Task task = new Task();
        task.setId(1L);
        task.setTitle("Daily report");
        task.setStatus("TODO");
        task.setAssigneeId(USER_ID);
        task.setCreatedAt(OffsetDateTime.now());

        when(taskRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Task>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(task)));

        when(subtaskRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(commentRepository.findByTaskIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(attachmentRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(taskAssigneeRepository.findByTaskIdAndIsActiveTrue(1L)).thenReturn(List.of(assignment));

        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setFullName("Test User");
        profile.setDepartment("HR");
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/tasks/assigned")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id", is(1)))
            .andExpect(jsonPath("$.items[0].assignees", hasSize(1)));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }
}
