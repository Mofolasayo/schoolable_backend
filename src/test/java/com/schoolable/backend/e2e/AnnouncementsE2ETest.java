package com.schoolable.backend.e2e;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolable.backend.announcement.Announcement;
import com.schoolable.backend.announcement.AnnouncementReadRepository;
import com.schoolable.backend.announcement.AnnouncementRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AnnouncementsE2ETest extends BaseE2ETest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ANNOUNCEMENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementReadRepository announcementReadRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @BeforeEach
    void cleanup() {
        announcementReadRepository.deleteAll();
        announcementRepository.deleteAll();
        profileRepository.deleteAll();
    }

    @Test
    void listAnnouncementsReturnsPublished() throws Exception {
        Profile profile = new Profile();
        profile.setId(USER_ID);
        profile.setDepartment("Engineering");
        profile.setRole("employee");
        profileRepository.save(profile);

        Announcement announcement = new Announcement();
        announcement.setId(ANNOUNCEMENT_ID);
        announcement.setTitle("Quarterly Update");
        announcement.setContent("Details");
        announcement.setAudience("All Staff");
        announcement.setPinned(false);
        announcement.setStatus("Published");
        announcement.setAuthorId(USER_ID);
        announcement.setCreatedAt(OffsetDateTime.now());
        announcementRepository.save(announcement);

        var auth = new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());

        mockMvc.perform(get("/announcements")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(ANNOUNCEMENT_ID.toString()));
    }
}
