package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentPillarRepository extends JpaRepository<DepartmentPillar, UUID> {

    List<DepartmentPillar> findByProfileIdOrderBySortOrderAsc(UUID profileId);

    List<DepartmentPillar> findByProfileIdAndIsActiveTrueOrderBySortOrderAsc(UUID profileId);

    @Query("SELECT p FROM DepartmentPillar p LEFT JOIN FETCH p.metrics WHERE p.profile.id = :profileId ORDER BY p.sortOrder")
    List<DepartmentPillar> findByProfileIdWithMetrics(@Param("profileId") UUID profileId);
}
