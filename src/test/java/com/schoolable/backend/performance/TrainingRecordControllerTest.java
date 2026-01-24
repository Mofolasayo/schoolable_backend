package com.schoolable.backend.performance;

import com.schoolable.backend.storage.StorageService;
import com.schoolable.backend.notification.NotificationService;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainingRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrainingRecordControllerTest {

    private static final String USER_ID = "14141414-1414-1414-1414-141414141414";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrainingRecordRepository trainingRecordRepository;

    @MockBean
    private StorageService storageService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void getCurrentQuarterStatus_returnsNotSubmitted() throws Exception {
        when(trainingRecordRepository.findByEmployeeIdAndQuarterAndYear(any(), any(), any()))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/performance/training-records/my/current-quarter")
                .principal(userAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasSubmitted", is(false)));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, "n/a", List.of());
    }
}
