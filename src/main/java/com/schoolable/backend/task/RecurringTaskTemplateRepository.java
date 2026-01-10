package com.schoolable.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringTaskTemplateRepository extends JpaRepository<RecurringTaskTemplate, UUID> {

    /**
     * Find all active templates due today or earlier
     */
    List<RecurringTaskTemplate> findByIsActiveTrueAndNextOccurrenceLessThanEqual(LocalDate date);

    /**
     * Find templates by creator
     */
    List<RecurringTaskTemplate> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    /**
     * Find templates by organization
     */
    List<RecurringTaskTemplate> findByOrganizationAndIsActiveTrue(String organization);

    List<RecurringTaskTemplate> findByIsActiveTrueOrderByCreatedAtDesc();
}
