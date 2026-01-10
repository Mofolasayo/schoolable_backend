package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {

    List<TaskTemplate> findByDepartmentAndIsActiveTrueOrderByUsageCountDesc(String department);
    
    List<TaskTemplate> findByOrganizationAndIsActiveTrueOrderByUsageCountDesc(String organization);
    
    List<TaskTemplate> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);
    
    List<TaskTemplate> findByIsActiveTrueOrderByUsageCountDesc();
}
