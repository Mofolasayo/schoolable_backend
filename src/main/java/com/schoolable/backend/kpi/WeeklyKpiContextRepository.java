package com.schoolable.backend.kpi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WeeklyKpiContextRepository extends JpaRepository<WeeklyKpiContext, UUID> {
    Optional<WeeklyKpiContext> findBySubjectTypeAndSubjectIdAndWeekNumberAndYear(
        String subjectType,
        UUID subjectId,
        Integer weekNumber,
        Integer year
    );
}
