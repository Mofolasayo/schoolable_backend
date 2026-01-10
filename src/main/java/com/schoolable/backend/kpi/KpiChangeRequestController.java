package com.schoolable.backend.kpi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/kpi/change-requests")
public class KpiChangeRequestController {

    private final KpiChangeRequestRepository changeRequestRepository;
    private final TeamKpiRepository teamKpiRepository;
    private final IndividualKpiRepository individualKpiRepository;
    private final KpiHistoryRepository kpiHistoryRepository;
    private final ObjectMapper objectMapper;

    public KpiChangeRequestController(
            KpiChangeRequestRepository changeRequestRepository,
            TeamKpiRepository teamKpiRepository,
            IndividualKpiRepository individualKpiRepository,
            KpiHistoryRepository kpiHistoryRepository,
            ObjectMapper objectMapper) {
        this.changeRequestRepository = changeRequestRepository;
        this.teamKpiRepository = teamKpiRepository;
        this.individualKpiRepository = individualKpiRepository;
        this.kpiHistoryRepository = kpiHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "PENDING") String status, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        KpiChangeRequest.Status reqStatus = KpiChangeRequest.Status.valueOf(status.toUpperCase());
        List<KpiChangeRequest> requests = changeRequestRepository.findByStatusOrderByRequestedAtDesc(reqStatus);
        return ResponseEntity.ok(Map.of("requests", requests));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID id, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        KpiChangeRequest request = changeRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Request not found"));
        }

        applyChangeRequest(request, (UUID) auth.getPrincipal());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID id, @RequestBody RejectRequest reject, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        KpiChangeRequest request = changeRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Request not found"));
        }

        request.setStatus(KpiChangeRequest.Status.REJECTED);
        request.setReviewedBy((UUID) auth.getPrincipal());
        request.setReviewedAt(java.time.OffsetDateTime.now());
        request.setReviewNotes(reject.reason());
        changeRequestRepository.save(request);

        return ResponseEntity.ok(Map.of("success", true));
    }

    private void applyChangeRequest(KpiChangeRequest request, UUID approverId) {
        Map<String, Object> payload = parsePayload(request.getPayload());

        if ("team".equalsIgnoreCase(request.getKpiType())) {
            applyTeamChange(request, payload, approverId);
        } else if ("individual".equalsIgnoreCase(request.getKpiType())) {
            applyIndividualChange(request, payload, approverId);
        }

        request.setStatus(KpiChangeRequest.Status.APPROVED);
        request.setReviewedBy(approverId);
        request.setReviewedAt(java.time.OffsetDateTime.now());
        changeRequestRepository.save(request);
    }

    private void applyTeamChange(KpiChangeRequest request, Map<String, Object> payload, UUID approverId) {
        switch (request.getRequestType()) {
            case "CREATE" -> {
                TeamKpi kpi = new TeamKpi();
                kpi.setTeamLeadId(UUID.fromString(payload.get("teamLeadId").toString()));
                kpi.setDepartment((String) payload.get("department"));
                kpi.setName((String) payload.get("name"));
                kpi.setDescription((String) payload.get("description"));
                kpi.setTargetValue(new BigDecimal(payload.get("targetValue").toString()));
                kpi.setTargetUnit((String) payload.get("targetUnit"));
                kpi.setWeight(Integer.valueOf(payload.get("weight").toString()));
                kpi.setQuarter((String) payload.get("quarter"));
                kpi.setYear(Integer.valueOf(payload.get("year").toString()));
                kpi.setIsActive(true);
                kpi.setVersion(1);
                if (payload.get("progressSource") != null) {
                    kpi.setProgressSource(payload.get("progressSource").toString());
                }
                if (payload.get("progressConfig") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> progressConfig = (Map<String, Object>) payload.get("progressConfig");
                    kpi.setProgressConfig(progressConfig);
                }
                if (payload.get("autoProgressEnabled") != null) {
                    kpi.setAutoProgressEnabled(Boolean.parseBoolean(payload.get("autoProgressEnabled").toString()));
                }

                TeamKpi saved = teamKpiRepository.save(kpi);
                logHistory(saved.getId(), "team", null, toJson(payload), approverId, "Approved create", "create");
            }
            case "UPDATE" -> {
                TeamKpi kpi = teamKpiRepository.findById(request.getKpiId()).orElse(null);
                if (kpi == null) return;

                String before = toJson(kpiToMap(kpi));
                applyTeamChanges(kpi, payload);
                kpi.setVersion(kpi.getVersion() != null ? kpi.getVersion() + 1 : 1);
                TeamKpi saved = teamKpiRepository.save(kpi);
                logHistory(saved.getId(), "team", before, toJson(kpiToMap(saved)), approverId, "Approved update", "update");
            }
            case "DELETE" -> {
                TeamKpi kpi = teamKpiRepository.findById(request.getKpiId()).orElse(null);
                if (kpi == null) return;

                String before = toJson(kpiToMap(kpi));
                kpi.setIsActive(false);
                TeamKpi saved = teamKpiRepository.save(kpi);
                logHistory(saved.getId(), "team", before, toJson(kpiToMap(saved)), approverId, "Approved delete", "delete");
            }
            default -> {}
        }
    }

    private void applyIndividualChange(KpiChangeRequest request, Map<String, Object> payload, UUID approverId) {
        switch (request.getRequestType()) {
            case "CREATE" -> {
                IndividualKpi kpi = new IndividualKpi();
                kpi.setEmployeeId(UUID.fromString(payload.get("employeeId").toString()));
                kpi.setSetById(UUID.fromString(payload.get("setById").toString()));
                kpi.setDepartment((String) payload.get("department"));
                kpi.setName((String) payload.get("name"));
                kpi.setDescription((String) payload.get("description"));
                kpi.setTargetValue(new BigDecimal(payload.get("targetValue").toString()));
                kpi.setTargetUnit((String) payload.get("targetUnit"));
                kpi.setWeight(Integer.valueOf(payload.get("weight").toString()));
                kpi.setQuarter((String) payload.get("quarter"));
                kpi.setYear(Integer.valueOf(payload.get("year").toString()));
                kpi.approve(approverId);
                kpi.setVersion(1);
                if (payload.get("progressSource") != null) {
                    kpi.setProgressSource(payload.get("progressSource").toString());
                }
                if (payload.get("progressConfig") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> progressConfig = (Map<String, Object>) payload.get("progressConfig");
                    kpi.setProgressConfig(progressConfig);
                }
                if (payload.get("autoProgressEnabled") != null) {
                    kpi.setAutoProgressEnabled(Boolean.parseBoolean(payload.get("autoProgressEnabled").toString()));
                }

                IndividualKpi saved = individualKpiRepository.save(kpi);
                logHistory(saved.getId(), "individual", null, toJson(payload), approverId, "Approved create", "create");
            }
            case "UPDATE" -> {
                IndividualKpi kpi = individualKpiRepository.findById(request.getKpiId()).orElse(null);
                if (kpi == null) return;

                String before = toJson(individualToMap(kpi));
                applyIndividualChanges(kpi, payload);
                kpi.setVersion(kpi.getVersion() != null ? kpi.getVersion() + 1 : 1);
                IndividualKpi saved = individualKpiRepository.save(kpi);
                logHistory(saved.getId(), "individual", before, toJson(individualToMap(saved)), approverId, "Approved update", "update");
            }
            case "DELETE" -> {
                IndividualKpi kpi = individualKpiRepository.findById(request.getKpiId()).orElse(null);
                if (kpi == null) return;

                String before = toJson(individualToMap(kpi));
                kpi.setIsActive(false);
                IndividualKpi saved = individualKpiRepository.save(kpi);
                logHistory(saved.getId(), "individual", before, toJson(individualToMap(saved)), approverId, "Approved delete", "delete");
            }
            default -> {}
        }
    }

    private void applyTeamChanges(TeamKpi kpi, Map<String, Object> changes) {
        if (changes.get("name") != null) kpi.setName(changes.get("name").toString());
        if (changes.get("description") != null) kpi.setDescription(changes.get("description").toString());
        if (changes.get("targetValue") != null) kpi.setTargetValue(new BigDecimal(changes.get("targetValue").toString()));
        if (changes.get("targetUnit") != null) kpi.setTargetUnit(changes.get("targetUnit").toString());
        if (changes.get("weight") != null) kpi.setWeight(Integer.valueOf(changes.get("weight").toString()));
        if (changes.get("isActive") != null) kpi.setIsActive(Boolean.valueOf(changes.get("isActive").toString()));
        if (changes.get("progressSource") != null) kpi.setProgressSource(changes.get("progressSource").toString());
        if (changes.get("progressConfig") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> progressConfig = (Map<String, Object>) changes.get("progressConfig");
            kpi.setProgressConfig(progressConfig);
        }
        if (changes.get("autoProgressEnabled") != null) {
            kpi.setAutoProgressEnabled(Boolean.valueOf(changes.get("autoProgressEnabled").toString()));
        }
    }

    private void applyIndividualChanges(IndividualKpi kpi, Map<String, Object> changes) {
        if (changes.get("name") != null) kpi.setName(changes.get("name").toString());
        if (changes.get("description") != null) kpi.setDescription(changes.get("description").toString());
        if (changes.get("targetValue") != null) kpi.setTargetValue(new BigDecimal(changes.get("targetValue").toString()));
        if (changes.get("targetUnit") != null) kpi.setTargetUnit(changes.get("targetUnit").toString());
        if (changes.get("weight") != null) kpi.setWeight(Integer.valueOf(changes.get("weight").toString()));
        if (changes.get("currentValue") != null) kpi.setCurrentValue(new BigDecimal(changes.get("currentValue").toString()));
        if (changes.get("isActive") != null) kpi.setIsActive(Boolean.valueOf(changes.get("isActive").toString()));
        if (changes.get("progressSource") != null) kpi.setProgressSource(changes.get("progressSource").toString());
        if (changes.get("progressConfig") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> progressConfig = (Map<String, Object>) changes.get("progressConfig");
            kpi.setProgressConfig(progressConfig);
        }
        if (changes.get("autoProgressEnabled") != null) {
            kpi.setAutoProgressEnabled(Boolean.valueOf(changes.get("autoProgressEnabled").toString()));
        }
    }

    private Map<String, Object> parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void logHistory(UUID kpiId, String kpiType, String before, String after, UUID changedBy, String reason, String field) {
        KpiHistory history = new KpiHistory(kpiId, kpiType, before, after, changedBy, reason, field);
        history.setChangedAt(LocalDateTime.now());
        kpiHistoryRepository.save(history);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    private Map<String, Object> kpiToMap(TeamKpi kpi) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", kpi.getId());
        map.put("name", kpi.getName());
        map.put("description", kpi.getDescription());
        map.put("targetValue", kpi.getTargetValue());
        map.put("targetUnit", kpi.getTargetUnit());
        map.put("weight", kpi.getWeight());
        map.put("quarter", kpi.getQuarter());
        map.put("year", kpi.getYear());
        map.put("isActive", kpi.getIsActive());
        map.put("version", kpi.getVersion());
        map.put("progressSource", kpi.getProgressSource());
        map.put("progressConfig", kpi.getProgressConfig());
        map.put("autoProgressEnabled", kpi.getAutoProgressEnabled());
        return map;
    }

    private Map<String, Object> individualToMap(IndividualKpi kpi) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", kpi.getId());
        map.put("name", kpi.getName());
        map.put("description", kpi.getDescription());
        map.put("targetValue", kpi.getTargetValue());
        map.put("targetUnit", kpi.getTargetUnit());
        map.put("weight", kpi.getWeight());
        map.put("quarter", kpi.getQuarter());
        map.put("year", kpi.getYear());
        map.put("isActive", kpi.getIsActive());
        map.put("version", kpi.getVersion());
        map.put("progressSource", kpi.getProgressSource());
        map.put("progressConfig", kpi.getProgressConfig());
        map.put("autoProgressEnabled", kpi.getAutoProgressEnabled());
        return map;
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    public record RejectRequest(String reason) {}
}
