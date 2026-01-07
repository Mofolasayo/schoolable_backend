package com.schoolable.backend.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PulseSurveyRepository extends JpaRepository<PulseSurvey, UUID> {
    
    @Query("SELECT s FROM PulseSurvey s WHERE s.isActive = true ORDER BY s.createdAt DESC LIMIT 1")
    Optional<PulseSurvey> findLatestActive();

    List<PulseSurvey> findByIsActiveTrue();
}
