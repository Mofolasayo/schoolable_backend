package com.schoolable.backend.survey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/surveys")
@Tag(name = "Surveys", description = "Pulse surveys and feedback")
public class SurveyController {

    private final PulseSurveyRepository surveyRepository;
    private final PulseSurveyResponseRepository responseRepository;

    public SurveyController(PulseSurveyRepository surveyRepository, PulseSurveyResponseRepository responseRepository) {
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
    }

    @Operation(summary = "Get current active pulse survey")
    @GetMapping("/pulse/current")
    public ResponseEntity<?> getCurrentPulseSurvey(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<PulseSurvey> surveyOpt = surveyRepository.findLatestActive();
        if (surveyOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("has_survey", false));
        }

        PulseSurvey survey = surveyOpt.get();

        // Check if user has already responded
        boolean hasResponded = responseRepository.existsBySurveyIdAndUserId(survey.getId(), userId);
        if (hasResponded) {
            return ResponseEntity.ok(Map.of("has_survey", false));
        }

        return ResponseEntity.ok(Map.of(
            "has_survey", true,
            "id", survey.getId(),
            "question", survey.getQuestion(),
            "category", survey.getCategory()
        ));
    }

    @Operation(summary = "Respond to a pulse survey")
    @PostMapping("/pulse/{id}/respond")
    public ResponseEntity<?> respondToSurvey(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> payload,
            Authentication auth) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        UUID userId = (UUID) auth.getPrincipal();

        Optional<PulseSurvey> surveyOpt = surveyRepository.findById(id);
        if (surveyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Survey not found"));
        }

        // Check for existing response
        if (responseRepository.existsBySurveyIdAndUserId(id, userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already responded to this survey"));
        }

        // Validate payload
        Integer rating = null;
        if (payload.containsKey("rating")) {
            rating = Integer.valueOf(payload.get("rating").toString());
        }
        String comment = (String) payload.get("comment");

        if (rating == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "rating is required"));
        }

        PulseSurveyResponse response = new PulseSurveyResponse();
        response.setSurveyId(id);
        response.setUserId(userId);
        response.setRating(rating);
        response.setComment(comment);
        response.setRespondedAt(OffsetDateTime.now());

        responseRepository.save(response);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Response submitted successfully"
        ));
    }
}
