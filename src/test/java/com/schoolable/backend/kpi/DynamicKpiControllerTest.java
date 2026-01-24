package com.schoolable.backend.kpi;

import com.schoolable.backend.audit.AuditService;
import com.schoolable.backend.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DynamicKpiController.class)
@AutoConfigureMockMvc(addFilters = false)
class DynamicKpiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamicKpiService dynamicKpiService;

    @MockBean
    private DepartmentKpiProfileRepository profileRepository;

    @MockBean
    private DepartmentPillarRepository pillarRepository;

    @MockBean
    private DepartmentMetricRepository metricRepository;

    @MockBean
    private ProfileRepository userProfileRepository;

    @MockBean
    private AuditService auditService;

    @Test
    void getAllDepartments_returnsStats() throws Exception {
        when(dynamicKpiService.getDepartmentAutomationStats())
            .thenReturn(List.of(Map.of("department", "Sales")));

        mockMvc.perform(get("/api/kpi/config/departments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].department", is("Sales")));
    }
}
