package com.schoolable.backend.kpi;

import com.schoolable.backend.audit.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing dynamic department KPI profiles.
 * Replaces the hardcoded DepartmentKpiConfig.java.
 */
@Service
public class DynamicKpiService {

    @Autowired
    private DepartmentKpiProfileRepository profileRepository;

    @Autowired
    private DepartmentPillarRepository pillarRepository;

    @Autowired
    private DepartmentMetricRepository metricRepository;

    @Autowired
    private AuditService auditService;

    /**
     * Get KPI profile for a department.
     * Falls back to 'Default' profile if department not found.
     */
    public DepartmentKpiProfile getProfileForDepartment(String department) {
        if (department == null || department.isBlank()) {
            return getDefaultProfile();
        }

        return profileRepository.findByDepartmentIgnoreCase(department)
            .orElseGet(this::getDefaultProfile);
    }

    /**
     * Get the default KPI profile.
     */
    public DepartmentKpiProfile getDefaultProfile() {
        return profileRepository.findByDepartment("Default")
            .orElseThrow(() -> new RuntimeException("Default department profile not found"));
    }

    /**
     * Get all active department profiles.
     */
    public List<DepartmentKpiProfile> getAllActiveProfiles() {
        return profileRepository.findByIsActiveTrue();
    }

    /**
     * Get department profile with full pillar and metric details.
     */
    public Optional<DepartmentKpiProfile> getFullProfile(String department) {
        return profileRepository.findByDepartmentWithPillars(department);
    }

    /**
     * Create a new department KPI profile.
     */
    @Transactional
    public DepartmentKpiProfile createProfile(String department, String displayName, String description, UUID createdBy) {
        if (profileRepository.existsByDepartmentIgnoreCase(department)) {
            throw new IllegalArgumentException("Department profile already exists: " + department);
        }

        DepartmentKpiProfile profile = new DepartmentKpiProfile(department, displayName);
        profile.setDescription(description);
        profile.setCreatedBy(createdBy);

        profile = profileRepository.save(profile);
        
        auditService.log(AuditService.ENTITY_KPI, profile.getId().toString(), 
            AuditService.ACTION_CREATE, null, Map.of("department", department, "displayName", displayName));

        return profile;
    }

    /**
     * Add a pillar to a department profile.
     */
    @Transactional
    public DepartmentPillar addPillar(UUID profileId, String pillarKey, String displayName, Integer weight, Integer sortOrder) {
        DepartmentKpiProfile profile = profileRepository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));

        // Validate total weight doesn't exceed 100
        int currentWeight = profile.getTotalWeight();
        if (currentWeight + weight > 100) {
            throw new IllegalArgumentException("Total pillar weights would exceed 100%. Current: " + currentWeight + ", Adding: " + weight);
        }

        DepartmentPillar pillar = new DepartmentPillar(pillarKey, displayName, weight);
        pillar.setSortOrder(sortOrder != null ? sortOrder : profile.getPillars().size());
        profile.addPillar(pillar);

        pillar = pillarRepository.save(pillar);
        
        auditService.log(AuditService.ENTITY_KPI, pillar.getId().toString(),
            AuditService.ACTION_CREATE, null, 
            Map.of("profileId", profileId, "pillarKey", pillarKey, "weight", weight));

        return pillar;
    }

    /**
     * Add a metric to a pillar.
     */
    @Transactional
    public DepartmentMetric addMetric(UUID pillarId, String metricKey, String displayName, 
                                       Integer weightInPillar, String source, String dataSource,
                                       BigDecimal targetValue, String description) {
        DepartmentPillar pillar = pillarRepository.findById(pillarId)
            .orElseThrow(() -> new IllegalArgumentException("Pillar not found: " + pillarId));

        // Validate total metric weight doesn't exceed 100
        int currentWeight = pillar.getTotalMetricWeight();
        if (currentWeight + weightInPillar > 100) {
            throw new IllegalArgumentException("Total metric weights would exceed 100%. Current: " + currentWeight + ", Adding: " + weightInPillar);
        }

        DepartmentMetric metric = new DepartmentMetric(metricKey, displayName, weightInPillar, source, dataSource);
        metric.setTargetValue(targetValue);
        metric.setDescription(description);
        metric.setSortOrder(pillar.getMetrics().size());
        pillar.addMetric(metric);

        metric = metricRepository.save(metric);

        auditService.log(AuditService.ENTITY_KPI, metric.getId().toString(),
            AuditService.ACTION_CREATE, null,
            Map.of("pillarId", pillarId, "metricKey", metricKey, "source", source));

        return metric;
    }

    /**
     * Update a pillar's weight.
     */
    @Transactional
    public DepartmentPillar updatePillarWeight(UUID pillarId, Integer newWeight) {
        DepartmentPillar pillar = pillarRepository.findById(pillarId)
            .orElseThrow(() -> new IllegalArgumentException("Pillar not found: " + pillarId));

        Integer oldWeight = pillar.getWeight();
        DepartmentKpiProfile profile = pillar.getProfile();
        
        int otherPillarsWeight = profile.getTotalWeight() - oldWeight;
        if (otherPillarsWeight + newWeight > 100) {
            throw new IllegalArgumentException("Total pillar weights would exceed 100%");
        }

        pillar.setWeight(newWeight);
        pillar.setUpdatedAt(OffsetDateTime.now());
        pillar = pillarRepository.save(pillar);

        auditService.log(AuditService.ENTITY_KPI, pillarId.toString(),
            AuditService.ACTION_UPDATE, Map.of("weight", oldWeight), Map.of("weight", newWeight));

        return pillar;
    }

    /**
     * Update a metric's configuration.
     */
    @Transactional
    public DepartmentMetric updateMetric(UUID metricId, Integer weightInPillar, String displayName,
                                         BigDecimal targetValue, String description, Boolean isActive) {
        DepartmentMetric metric = metricRepository.findById(metricId)
            .orElseThrow(() -> new IllegalArgumentException("Metric not found: " + metricId));

        Map<String, Object> before = Map.of(
            "weightInPillar", metric.getWeightInPillar(),
            "displayName", metric.getDisplayName(),
            "isActive", metric.getIsActive()
        );

        if (weightInPillar != null) metric.setWeightInPillar(weightInPillar);
        if (displayName != null) metric.setDisplayName(displayName);
        if (targetValue != null) metric.setTargetValue(targetValue);
        if (description != null) metric.setDescription(description);
        if (isActive != null) metric.setIsActive(isActive);
        metric.setUpdatedAt(OffsetDateTime.now());

        metric = metricRepository.save(metric);

        Map<String, Object> after = Map.of(
            "weightInPillar", metric.getWeightInPillar(),
            "displayName", metric.getDisplayName(),
            "isActive", metric.getIsActive()
        );

        auditService.log(AuditService.ENTITY_KPI, metricId.toString(),
            AuditService.ACTION_UPDATE, before, after);

        return metric;
    }

    /**
     * Delete a metric.
     */
    @Transactional
    public void deleteMetric(UUID metricId) {
        DepartmentMetric metric = metricRepository.findById(metricId)
            .orElseThrow(() -> new IllegalArgumentException("Metric not found: " + metricId));

        auditService.log(AuditService.ENTITY_KPI, metricId.toString(),
            AuditService.ACTION_DELETE, Map.of("metricKey", metric.getMetricKey()), null);

        metricRepository.delete(metric);
    }

    /**
     * Get all departments with their automation percentages.
     */
    public List<Map<String, Object>> getDepartmentAutomationStats() {
        return profileRepository.findAllActiveWithPillars().stream()
            .map(profile -> {
                Map<String, Object> stats = new HashMap<>();
                stats.put("department", profile.getDepartment());
                stats.put("displayName", profile.getDisplayName());
                stats.put("totalMetrics", profile.getTotalMetricCount());
                stats.put("autoMetrics", profile.getAutoMetricCount());
                stats.put("automationPercentage", profile.getAutomationPercentage());
                stats.put("pillars", profile.getPillars().stream()
                    .map(p -> Map.of(
                        "key", p.getPillarKey(),
                        "name", p.getDisplayName(),
                        "weight", p.getWeight()
                    ))
                    .collect(Collectors.toList()));
                return stats;
            })
            .collect(Collectors.toList());
    }

    /**
     * Clone a profile for another department.
     */
    @Transactional
    public DepartmentKpiProfile cloneProfile(String sourceDepartment, String targetDepartment, 
                                              String displayName, UUID createdBy) {
        DepartmentKpiProfile source = getFullProfile(sourceDepartment)
            .orElseThrow(() -> new IllegalArgumentException("Source profile not found: " + sourceDepartment));

        if (profileRepository.existsByDepartmentIgnoreCase(targetDepartment)) {
            throw new IllegalArgumentException("Target department already exists: " + targetDepartment);
        }

        DepartmentKpiProfile target = new DepartmentKpiProfile(targetDepartment, displayName);
        target.setDescription("Cloned from " + sourceDepartment);
        target.setCreatedBy(createdBy);
        target = profileRepository.save(target);

        // Clone pillars and metrics
        for (DepartmentPillar sourcePillar : source.getPillars()) {
            DepartmentPillar targetPillar = new DepartmentPillar(
                sourcePillar.getPillarKey(),
                sourcePillar.getDisplayName(),
                sourcePillar.getWeight()
            );
            targetPillar.setSortOrder(sourcePillar.getSortOrder());
            target.addPillar(targetPillar);
            targetPillar = pillarRepository.save(targetPillar);

            for (DepartmentMetric sourceMetric : sourcePillar.getMetrics()) {
                DepartmentMetric targetMetric = new DepartmentMetric(
                    sourceMetric.getMetricKey(),
                    sourceMetric.getDisplayName(),
                    sourceMetric.getWeightInPillar(),
                    sourceMetric.getSource(),
                    sourceMetric.getDataSource()
                );
                targetMetric.setTargetValue(sourceMetric.getTargetValue());
                targetMetric.setTargetUnit(sourceMetric.getTargetUnit());
                targetMetric.setDescription(sourceMetric.getDescription());
                targetMetric.setIsAutoCalculated(sourceMetric.getIsAutoCalculated());
                targetMetric.setSortOrder(sourceMetric.getSortOrder());
                targetPillar.addMetric(targetMetric);
                metricRepository.save(targetMetric);
            }
        }

        auditService.log(AuditService.ENTITY_KPI, target.getId().toString(),
            AuditService.ACTION_CREATE, null,
            Map.of("action", "clone", "source", sourceDepartment, "target", targetDepartment));

        return target;
    }
}
