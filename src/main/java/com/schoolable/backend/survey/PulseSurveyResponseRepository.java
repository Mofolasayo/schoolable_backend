package com.schoolable.backend.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PulseSurveyResponseRepository extends JpaRepository<PulseSurveyResponse, UUID> {
    
    Optional<PulseSurveyResponse> findBySurveyIdAndUserId(UUID surveyId, UUID userId);
    
    boolean existsBySurveyIdAndUserId(UUID surveyId, UUID userId);
}
