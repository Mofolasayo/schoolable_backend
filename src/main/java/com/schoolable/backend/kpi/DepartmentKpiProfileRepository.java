package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentKpiProfileRepository extends JpaRepository<DepartmentKpiProfile, UUID> {

    Optional<DepartmentKpiProfile> findByDepartment(String department);

    Optional<DepartmentKpiProfile> findByDepartmentIgnoreCase(String department);

    List<DepartmentKpiProfile> findByIsActiveTrue();

    @Query("SELECT p FROM DepartmentKpiProfile p LEFT JOIN FETCH p.pillars WHERE p.department = :department")
    Optional<DepartmentKpiProfile> findByDepartmentWithPillars(@Param("department") String department);

    @Query("SELECT p FROM DepartmentKpiProfile p LEFT JOIN FETCH p.pillars WHERE p.isActive = true")
    List<DepartmentKpiProfile> findAllActiveWithPillars();

    boolean existsByDepartmentIgnoreCase(String department);
}
