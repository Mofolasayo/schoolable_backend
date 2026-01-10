package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Aura Score Explanation Controller
 * Provides detailed breakdowns of how Aura scores are calculated.
 * Helps employees understand their scores and how to improve them.
 */
@RestController
@RequestMapping("/aura")
@Tag(name = "Aura Score Explanation", description = "Score breakdown and improvement tips")
public class AuraExplanationController {

    @Autowired
    private AutoAuraCalculationService auraCalculationService;

    @Autowired
    private ProfileRepository profileRepository;

    /**
     * Get detailed score explanation for an employee
     */
    @GetMapping("/explanation/{employeeId}")
    @Operation(summary = "Get score explanation", 
               description = "Returns detailed breakdown of how the Aura score is calculated")
    public ResponseEntity<?> getScoreExplanation(@PathVariable UUID employeeId) {
        Profile profile = profileRepository.findById(employeeId).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        }

        return ResponseEntity.ok(buildExplanation(profile));
    }

    /**
     * Get my score explanation (current user)
     */
    @GetMapping("/explanation/me")
    @Operation(summary = "Get my score explanation", 
               description = "Returns detailed breakdown of your own Aura score")
    public ResponseEntity<?> getMyScoreExplanation(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        Profile profile = profileRepository.findById(userId).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
        }

        return ResponseEntity.ok(buildExplanation(profile));
    }

    /**
     * Build the detailed score explanation
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildExplanation(Profile profile) {
        // Get the calculated score with all details
        Map<String, Object> scoreData = auraCalculationService.calculateEmployeeScore(profile);
        
        Map<String, Object> explanation = new LinkedHashMap<>();
        
        // Basic info
        explanation.put("employeeId", profile.getId().toString());
        explanation.put("employeeName", profile.getFullName());
        explanation.put("department", profile.getDepartment());
        explanation.put("generatedAt", LocalDateTime.now().toString());
        
        // Overall score
        explanation.put("overallScore", scoreData.get("auraScore"));
        explanation.put("grade", scoreData.get("grade"));
        explanation.put("qgpa", scoreData.get("qgpa"));
        
        // Onboarding status
        if (Boolean.TRUE.equals(scoreData.get("isOnboarding"))) {
            explanation.put("onboardingStatus", Map.of(
                "isOnboarding", true,
                "daysEmployed", scoreData.get("daysEmployed"),
                "daysRemaining", scoreData.get("onboardingDaysRemaining"),
                "message", scoreData.get("onboardingMessage")
            ));
        }
        
        // Pillar breakdown with explanations
        Map<String, Map<String, Object>> pillars = 
            (Map<String, Map<String, Object>>) scoreData.get("pillars");
        
        List<Map<String, Object>> pillarExplanations = new ArrayList<>();
        List<Map<String, Object>> improvementTips = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : pillars.entrySet()) {
            String pillarKey = entry.getKey();
            Map<String, Object> pillarData = entry.getValue();
            
            Map<String, Object> pillarExplanation = new LinkedHashMap<>();
            pillarExplanation.put("pillarKey", pillarKey);
            pillarExplanation.put("pillarName", pillarData.get("name"));
            pillarExplanation.put("score", pillarData.get("score"));
            pillarExplanation.put("weight", pillarData.get("weight"));
            pillarExplanation.put("contribution", pillarData.get("contribution"));
            pillarExplanation.put("dataSource", pillarData.get("dataSource"));
            
            // Add human-readable explanation
            double score = (double) pillarData.get("score");
            pillarExplanation.put("interpretation", getScoreInterpretation(score));
            
            // Sub-metrics breakdown
            pillarExplanation.put("subMetrics", pillarData.get("subMetrics"));
            
            // Check if adjusted for onboarding
            if (Boolean.TRUE.equals(pillarData.get("onboardingAdjusted"))) {
                pillarExplanation.put("note", 
                    "Baseline score applied - you're still building history for this metric");
            }
            
            pillarExplanations.add(pillarExplanation);
            
            // Generate improvement tips for low-scoring pillars
            if (score < 70) {
                improvementTips.add(generateImprovementTip(pillarKey, score, pillarData));
            }
        }
        
        explanation.put("pillarBreakdown", pillarExplanations);
        
        // Improvement suggestions
        if (!improvementTips.isEmpty()) {
            explanation.put("improvementTips", improvementTips);
        }
        
        // How the score affects them
        explanation.put("scoreImpact", getScoreImpact((Double) scoreData.get("auraScore")));
        
        return explanation;
    }
    
    private String getScoreInterpretation(double score) {
        if (score >= 90) return "Excellent - Top performer in this area";
        if (score >= 80) return "Great - Above average performance";
        if (score >= 70) return "Good - Meeting expectations";
        if (score >= 60) return "Fair - Some room for improvement";
        if (score >= 50) return "Needs Attention - Below expectations";
        return "Critical - Requires immediate focus";
    }
    
    private Map<String, Object> generateImprovementTip(String pillarKey, double score, 
                                                        Map<String, Object> pillarData) {
        Map<String, Object> tip = new HashMap<>();
        tip.put("pillar", pillarKey);
        tip.put("currentScore", score);
        tip.put("targetScore", 75.0);
        
        switch (pillarKey.toLowerCase()) {
            case "technical_competence":
            case "technical":
                tip.put("title", "Boost Your Technical Score");
                tip.put("actions", List.of(
                    "Complete daily reports with detailed task descriptions",
                    "Focus on on-time task delivery",
                    "Document your work thoroughly",
                    "Request feedback on completed tasks"
                ));
                break;
            case "behavioral":
            case "professional_conduct":
                tip.put("title", "Improve Professional Conduct");
                tip.put("actions", List.of(
                    "Proactively help teammates when blocked",
                    "Follow compliance procedures carefully",
                    "Participate actively in team meetings",
                    "Communicate blockers early"
                ));
                break;
            case "cultural_fit":
            case "culture":
                tip.put("title", "Strengthen Cultural Alignment");
                tip.put("actions", List.of(
                    "Check in on time consistently",
                    "Engage with company announcements",
                    "Participate in optional team events",
                    "Share knowledge with colleagues"
                ));
                break;
            case "growth":
            case "growth_learning":
                tip.put("title", "Accelerate Your Growth");
                tip.put("actions", List.of(
                    "Complete at least one training this quarter",
                    "Upload relevant certifications",
                    "Seek feedback from your team lead",
                    "Track improvement vs last quarter"
                ));
                break;
            default:
                tip.put("title", "Improve " + pillarKey.replace("_", " "));
                tip.put("actions", List.of(
                    "Review your performance data for this area",
                    "Discuss with your manager for specific guidance"
                ));
        }
        
        return tip;
    }
    
    private Map<String, Object> getScoreImpact(Double score) {
        Map<String, Object> impact = new HashMap<>();
        
        if (score >= 85) {
            impact.put("level", "High Performer");
            impact.put("benefits", List.of(
                "Eligible for performance bonuses",
                "Priority for promotion consideration",
                "May be assigned leadership opportunities"
            ));
        } else if (score >= 70) {
            impact.put("level", "Solid Contributor");
            impact.put("benefits", List.of(
                "Meets performance expectations",
                "Eligible for standard advancement",
                "Focus areas identified for growth"
            ));
        } else if (score >= 50) {
            impact.put("level", "Development Focus");
            impact.put("benefits", List.of(
                "Performance improvement plan may be discussed",
                "Additional support available from team lead",
                "Focus on improvement tips above"
            ));
        } else {
            impact.put("level", "Immediate Attention Required");
            impact.put("benefits", List.of(
                "Discuss with your manager urgently",
                "Identify blockers affecting performance",
                "Create action plan for improvement"
            ));
        }
        
        return impact;
    }
}
