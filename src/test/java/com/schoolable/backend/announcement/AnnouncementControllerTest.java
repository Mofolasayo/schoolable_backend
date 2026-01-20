package com.schoolable.backend.announcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnnouncementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnnouncementControllerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ANNOUNCEMENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnnouncementRepository announcementRepository;

    @MockBean
    private AnnouncementReadRepository announcementReadRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void getUnreadAnnouncements_filtersReadAndAudience() throws Exception {
        Profile profile = buildProfile("employee", "HR", false);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        UUID readId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        AnnouncementRead read = new AnnouncementRead(USER_ID, readId);
        when(announcementReadRepository.findByUserId(USER_ID)).thenReturn(List.of(read));

        Announcement readAnnouncement = buildAnnouncement(readId, "All Staff");
        Announcement departmentAnnouncement = buildAnnouncement(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), "HR");
        Announcement teamLeadAnnouncement = buildAnnouncement(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"), "Team Leads");
        when(announcementRepository.findActiveAnnouncements())
                .thenReturn(List.of(readAnnouncement, departmentAnnouncement, teamLeadAnnouncement));

        mockMvc.perform(get("/announcements/unread")
                .principal(auth(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(departmentAnnouncement.getId().toString()))
            .andExpect(jsonPath("$[0].is_read").value(false));
    }

    @Test
    void createAnnouncement_teamLeadForcesDepartmentAudience() throws Exception {
        Profile profile = buildProfile("employee", "Sales", true);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(announcementRepository.save(any(Announcement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String scheduledAt = "2025-01-20T10:00:00Z";

        mockMvc.perform(post("/announcements")
                .principal(auth(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                        "title", "Policy Update",
                        "content", "Please review the new policy.",
                        "audience", "All Staff",
                        "pinned", true,
                        "status", "Scheduled",
                        "scheduledAt", scheduledAt
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audience").value("Sales"))
            .andExpect(jsonPath("$.status").value("Scheduled"));

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        Announcement saved = captor.getValue();
        assertEquals("Sales", saved.getAudience());
        assertEquals("Scheduled", saved.getStatus());
        assertEquals(OffsetDateTime.parse(scheduledAt), saved.getScheduledAt());
    }

    @Test
    void deleteAnnouncement_rejectsNonAdmin() throws Exception {
        Profile profile = buildProfile("employee", "HR", true);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        mockMvc.perform(delete("/announcements/{id}", ANNOUNCEMENT_ID)
                .principal(auth(USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Only admins can delete announcements"));

        verify(announcementRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void markAsRead_returns404WhenAnnouncementMissing() throws Exception {
        when(announcementRepository.existsById(ANNOUNCEMENT_ID)).thenReturn(false);

        mockMvc.perform(post("/announcements/{id}/read", ANNOUNCEMENT_ID)
                .principal(auth(USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Announcement not found"));

        verify(announcementReadRepository, never()).save(any(AnnouncementRead.class));
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of());
    }

    private Profile buildProfile(String role, String department, boolean isTeamLead) {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setRole(role);
        profile.setDepartment(department);
        profile.setIsTeamLead(isTeamLead);
        return profile;
    }

    private Announcement buildAnnouncement(UUID id, String audience) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle("Announcement");
        announcement.setContent("Details");
        announcement.setAudience(audience);
        announcement.setPinned(false);
        announcement.setStatus("Published");
        announcement.setAuthorId(USER_ID);
        announcement.setCreatedAt(OffsetDateTime.now());
        return announcement;
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
