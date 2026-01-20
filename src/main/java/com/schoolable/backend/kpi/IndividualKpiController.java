package com.schoolable.backend.kpi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Individual KPIs
 * Team Leads set KPIs for each team member.
 */
@RestController
@RequestMapping("/api/individual-kpis")
@Tag(name = "Individual KPIs")
public class IndividualKpiController {

    @Autowired
    private IndividualKpiRepository individualKpiRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private KpiLockService kpiLockService;

    @Autowired
    private KpiChangeRequestRepository changeRequestRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== TEAM LEAD - CREATE/MANAGE ====================

    @Operation(summary = "Get KPIs I've set for my team members")
    @GetMapping("/my-team")
    public ResponseEntity<?> getTeamKpis(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Verify team lead
        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only team leads can manage individual KPIs"));
        }

        String q = quarter != null ? quarter : getCurrentQuarter();
        int y = year != null ? year : LocalDate.now().getYear();

        List<IndividualKpi> kpis = individualKpiRepository.findBySetByIdAndPeriod(userId, q, y);

        // Group by employee
        Map<UUID, List<Map<String, Object>>> byEmployee = new LinkedHashMap<>();
        for (IndividualKpi kpi : kpis) {
            byEmployee.computeIfAbsent(kpi.getEmployeeId(), k -> new ArrayList<>()).add(toDto(kpi));
        }

        // Build response with employee info
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<UUID, List<Map<String, Object>>> entry : byEmployee.entrySet()) {
            Profile emp = profileRepository.findById(entry.getKey()).orElse(null);
            int totalWeight = entry.getValue().stream()
                .mapToInt(k -> ((Number) k.get("weight")).intValue())
                .sum();
            
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("employeeId", entry.getKey().toString());
            item.put("employeeName", emp != null ? emp.getFullName() : "Unknown");
            item.put("employeeEmail", emp != null ? emp.getEmail() : null);
            item.put("employeeRole", emp != null ? emp.getJobTitle() : null);
            item.put("kpis", entry.getValue());
            item.put("totalWeight", totalWeight);
            item.put("isComplete", totalWeight >= 100);
            result.add(item);
        }

        return ResponseEntity.ok(Map.of(
            "quarter", q,
            "year", y,
            "employees", result
        ));
    }

    @Operation(summary = "Get team members without KPIs set")
    @GetMapping("/pending-setup")
    public ResponseEntity<?> getPendingSetup(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<Profile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty() || !Boolean.TRUE.equals(profileOpt.get().getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only team leads can view this"));
        }

        String department = profileOpt.get().getDepartment();
        String q = quarter != null ? quarter : getCurrentQuarter();
        int y = year != null ? year : LocalDate.now().getYear();

        // Get all team members
        List<Profile> teamMembers = profileRepository.findByDepartment(department);
        
        // Get employees who already have KPIs set
        List<IndividualKpi> existingKpis = individualKpiRepository.findBySetByIdAndPeriod(userId, q, y);
        Set<UUID> withKpis = existingKpis.stream()
            .map(IndividualKpi::getEmployeeId)
            .collect(Collectors.toSet());

        // Filter to those without KPIs
        List<Map<String, Object>> pending = teamMembers.stream()
            .filter(m -> !withKpis.contains(m.getId()) && !m.getId().equals(userId))
            .map(m -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", m.getId().toString());
                dto.put("name", m.getFullName());
                dto.put("email", m.getEmail());
                dto.put("role", m.getJobTitle());
                return dto;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "quarter", q,
            "year", y,
            "pendingCount", pending.size(),
            "employees", pending
        ));
    }

    @Operation(summary = "Create individual KPI for a team member")
    @PostMapping
    public ResponseEntity<?> createKpi(Authentication auth, @RequestBody CreateKpiRequest request) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        // Verify team lead
        Optional<Profile> leadProfile = profileRepository.findById(userId);
        if (leadProfile.isEmpty() || !Boolean.TRUE.equals(leadProfile.get().getIsTeamLead())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only team leads can create individual KPIs"));
        }

        // Validate employee exists and is in same department
        UUID employeeId = UUID.fromString(request.employeeId());
        Optional<Profile> empProfile = profileRepository.findById(employeeId);
        if (empProfile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee not found"));
        }

        String department = leadProfile.get().getDepartment();
        if (department != null && !department.equals(empProfile.get().getDepartment())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee is not in your department"));
        }

        String quarter = request.quarter() != null ? request.quarter() : getCurrentQuarter();
        int year = request.year() != null ? request.year() : LocalDate.now().getYear();

        if (kpiLockService.isLocked("individual", quarter, year, userId, department) && !isAdmin(auth)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("employeeId", request.employeeId());
            payload.put("setById", userId.toString());
            payload.put("department", department);
            payload.put("name", request.name());
            payload.put("description", request.description());
            payload.put("targetValue", request.targetValue());
            payload.put("targetUnit", request.targetUnit());
            payload.put("weight", request.weight());
            payload.put("quarter", quarter);
            payload.put("year", year);
            if (request.progressSource() != null) payload.put("progressSource", request.progressSource());
            if (request.progressConfig() != null) payload.put("progressConfig", request.progressConfig());
            if (request.autoProgressEnabled() != null) payload.put("autoProgressEnabled", request.autoProgressEnabled());

            return queueChangeRequest(
                "CREATE",
                null,
                payload,
                userId
            );
        }

        // Check total weight won't exceed 100
        Integer currentWeight = individualKpiRepository.getTotalWeight(employeeId, quarter, year);
        if (currentWeight != null && currentWeight + request.weight() > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Total weight would exceed 100%. Current: " + currentWeight + "%, Adding: " + request.weight() + "%"
            ));
        }

        IndividualKpi kpi = new IndividualKpi();
        kpi.setEmployeeId(employeeId);
        kpi.setSetById(userId);
        kpi.setDepartment(department);
        kpi.setName(request.name());
        kpi.setDescription(request.description());
        kpi.setTargetValue(BigDecimal.valueOf(request.targetValue()));
        kpi.setTargetUnit(request.targetUnit());
        kpi.setWeight(request.weight());
        kpi.setQuarter(quarter);
        kpi.setYear(year);
        String progressSource = request.progressSource() != null && !request.progressSource().isBlank()
            ? request.progressSource()
            : "DAILY_REPORT_KPI_ALIGNMENT";
        kpi.setProgressSource(progressSource);
        kpi.setProgressConfig(request.progressConfig());
        boolean autoProgressEnabled = request.autoProgressEnabled() != null
            ? request.autoProgressEnabled()
            : (progressSource != null && !progressSource.isBlank());
        kpi.setAutoProgressEnabled(autoProgressEnabled);

        kpi = individualKpiRepository.save(kpi);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Individual KPI created",
            "kpi", toDto(kpi)
        ));
    }

    @Operation(summary = "Update an individual KPI")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateKpi(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody UpdateKpiRequest request
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<IndividualKpi> kpiOpt = individualKpiRepository.findById(id);
        if (kpiOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "KPI not found"));
        }

        IndividualKpi kpi = kpiOpt.get();
        if (!kpi.getSetById().equals(userId) && !isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only edit KPIs you created"));
        }

        if (kpiLockService.isLocked("individual", kpi.getQuarter(), kpi.getYear(), kpi.getSetById(), kpi.getDepartment()) && !isAdmin(auth)) {
            Map<String, Object> changes = new LinkedHashMap<>();
            if (request.name() != null) changes.put("name", request.name());
            if (request.description() != null) changes.put("description", request.description());
            if (request.targetValue() != null) changes.put("targetValue", request.targetValue());
            if (request.currentValue() != null) changes.put("currentValue", request.currentValue());
            if (request.targetUnit() != null) changes.put("targetUnit", request.targetUnit());
            if (request.weight() != null) changes.put("weight", request.weight());
            if (request.isActive() != null) changes.put("isActive", request.isActive());
            if (request.progressSource() != null) changes.put("progressSource", request.progressSource());
            if (request.progressConfig() != null) changes.put("progressConfig", request.progressConfig());
            if (request.autoProgressEnabled() != null) changes.put("autoProgressEnabled", request.autoProgressEnabled());

            return queueChangeRequest("UPDATE", kpi.getId(), changes, userId);
        }

        // Update fields
        if (request.name() != null) kpi.setName(request.name());
        if (request.description() != null) kpi.setDescription(request.description());
        if (request.targetValue() != null) kpi.setTargetValue(BigDecimal.valueOf(request.targetValue()));
        if (request.currentValue() != null) kpi.setCurrentValue(BigDecimal.valueOf(request.currentValue()));
        if (request.targetUnit() != null) kpi.setTargetUnit(request.targetUnit());
        if (request.weight() != null) {
            // Validate new weight won't exceed 100
            Integer otherWeights = individualKpiRepository.getTotalWeight(kpi.getEmployeeId(), kpi.getQuarter(), kpi.getYear());
            int currentKpiWeight = kpi.getWeight();
            int newTotal = (otherWeights != null ? otherWeights - currentKpiWeight : 0) + request.weight();
            if (newTotal > 100) {
                return ResponseEntity.badRequest().body(Map.of("error", "Total weight would exceed 100%"));
            }
            kpi.setWeight(request.weight());
        }
        if (request.isActive() != null) kpi.setIsActive(request.isActive());
        if (request.progressSource() != null) {
            kpi.setProgressSource(request.progressSource());
            if (request.autoProgressEnabled() == null) {
                kpi.setAutoProgressEnabled(true);
            }
        }
        if (request.progressConfig() != null) kpi.setProgressConfig(request.progressConfig());
        if (request.autoProgressEnabled() != null) kpi.setAutoProgressEnabled(request.autoProgressEnabled());

        kpi.setVersion(kpi.getVersion() != null ? kpi.getVersion() + 1 : 1);
        kpi.setUpdatedAt(LocalDateTime.now());
        kpi = individualKpiRepository.save(kpi);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "KPI updated",
            "kpi", toDto(kpi)
        ));
    }

    @Operation(summary = "Delete an individual KPI")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKpi(Authentication auth, @PathVariable UUID id) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<IndividualKpi> kpiOpt = individualKpiRepository.findById(id);
        if (kpiOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "KPI not found"));
        }

        IndividualKpi kpi = kpiOpt.get();
        if (!kpi.getSetById().equals(userId) && !isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only delete KPIs you created"));
        }

        if (kpiLockService.isLocked("individual", kpi.getQuarter(), kpi.getYear(), kpi.getSetById(), kpi.getDepartment()) && !isAdmin(auth)) {
            return queueChangeRequest("DELETE", kpi.getId(), Map.of("kpiId", kpi.getId().toString()), userId);
        }

        individualKpiRepository.delete(kpi);

        return ResponseEntity.ok(Map.of("success", true, "message", "KPI deleted"));
    }

    // ==================== EMPLOYEE - VIEW MY KPIS ====================

    @Operation(summary = "Get my individual KPIs")
    @GetMapping("/my")
    public ResponseEntity<?> getMyKpis(
            Authentication auth,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        String q = quarter != null ? quarter : getCurrentQuarter();
        int y = year != null ? year : LocalDate.now().getYear();

        List<IndividualKpi> kpis = individualKpiRepository.findActiveByEmployeeAndPeriod(userId, q, y);
        Double avgAchievement = individualKpiRepository.getAverageAchievement(userId, q, y);
        Integer totalWeight = individualKpiRepository.getTotalWeight(userId, q, y);

        return ResponseEntity.ok(Map.of(
            "quarter", q,
            "year", y,
            "kpis", kpis.stream().map(this::toDto).collect(Collectors.toList()),
            "totalWeight", totalWeight != null ? totalWeight : 0,
            "averageAchievement", avgAchievement != null ? avgAchievement : 0
        ));
    }

    @Operation(summary = "Get individual KPIs for an employee")
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeKpis(
            Authentication auth,
            @PathVariable String employeeId,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        UUID userId = (UUID) auth.getPrincipal();
        UUID targetId;
        try {
            targetId = UUID.fromString(employeeId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid employeeId"));
        }

        Profile targetProfile = profileRepository.findById(targetId).orElse(null);
        if (targetProfile == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        }

        if (!isAdmin(auth)) {
            Profile requester = profileRepository.findById(userId).orElse(null);
            boolean isTeamLead = requester != null && Boolean.TRUE.equals(requester.getIsTeamLead());
            boolean sameDept = requester != null && requester.getDepartment() != null
                && requester.getDepartment().equals(targetProfile.getDepartment());
            if (!isTeamLead || !sameDept) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }
        }

        String q = quarter != null ? quarter : getCurrentQuarter();
        int y = year != null ? year : LocalDate.now().getYear();

        List<IndividualKpi> kpis = individualKpiRepository.findActiveByEmployeeAndPeriod(targetId, q, y);
        Double avgAchievement = individualKpiRepository.getAverageAchievement(targetId, q, y);
        Integer totalWeight = individualKpiRepository.getTotalWeight(targetId, q, y);

        return ResponseEntity.ok(Map.of(
            "employeeId", targetId.toString(),
            "quarter", q,
            "year", y,
            "kpis", kpis.stream().map(this::toDto).collect(Collectors.toList()),
            "totalWeight", totalWeight != null ? totalWeight : 0,
            "averageAchievement", avgAchievement != null ? avgAchievement : 0
        ));
    }

    // ==================== HELPERS ====================

    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    private ResponseEntity<?> queueChangeRequest(String requestType, UUID kpiId, Map<String, Object> payload, UUID requestedBy) {
        try {
            KpiChangeRequest changeRequest = new KpiChangeRequest();
            changeRequest.setKpiType("individual");
            changeRequest.setRequestType(requestType);
            changeRequest.setKpiId(kpiId);
            changeRequest.setRequestedBy(requestedBy);
            changeRequest.setPayload(objectMapper.writeValueAsString(payload));
            changeRequestRepository.save(changeRequest);

            return ResponseEntity.accepted().body(Map.of(
                "success", true,
                "status", "PENDING",
                "changeRequestId", changeRequest.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to queue change request"
            ));
        }
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private Map<String, Object> toDto(IndividualKpi kpi) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", kpi.getId().toString());
        dto.put("employeeId", kpi.getEmployeeId().toString());
        dto.put("setById", kpi.getSetById().toString());
        dto.put("department", kpi.getDepartment());
        dto.put("name", kpi.getName());
        dto.put("description", kpi.getDescription());
        dto.put("targetValue", kpi.getTargetValue());
        dto.put("currentValue", kpi.getCurrentValue());
        dto.put("targetUnit", kpi.getTargetUnit());
        dto.put("weight", kpi.getWeight());
        dto.put("quarter", kpi.getQuarter());
        dto.put("year", kpi.getYear());
        dto.put("isActive", kpi.getIsActive());
        dto.put("achievementPercentage", kpi.getAchievementPercentage());
        dto.put("createdAt", kpi.getCreatedAt());
        dto.put("updatedAt", kpi.getUpdatedAt());
        dto.put("version", kpi.getVersion());
        dto.put("progressSource", kpi.getProgressSource());
        dto.put("progressConfig", kpi.getProgressConfig());
        dto.put("autoProgressEnabled", kpi.getAutoProgressEnabled());
        dto.put("lastProgressSyncAt", kpi.getLastProgressSyncAt());
        return dto;
    }

    // Request DTOs
    public record CreateKpiRequest(
        String employeeId,
        String name,
        String description,
        Double targetValue,
        String targetUnit,
        Integer weight,
        String quarter,
        Integer year,
        String progressSource,
        Map<String, Object> progressConfig,
        Boolean autoProgressEnabled
    ) {}

    public record UpdateKpiRequest(
        String name,
        String description,
        Double targetValue,
        Double currentValue,
        String targetUnit,
        Integer weight,
        Boolean isActive,
        String progressSource,
        Map<String, Object> progressConfig,
        Boolean autoProgressEnabled
    ) {}
}
