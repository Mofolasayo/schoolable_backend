package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentMetricRepository extends JpaRepository<DepartmentMetric, UUID> {

    List<DepartmentMetric> findByPillarIdOrderBySortOrderAsc(UUID pillarId);

    List<DepartmentMetric> findByPillarIdAndIsActiveTrueOrderBySortOrderAsc(UUID pillarId);

    List<DepartmentMetric> findByIsAutoCalculatedTrue();

    List<DepartmentMetric> findBySource(String source);
}
