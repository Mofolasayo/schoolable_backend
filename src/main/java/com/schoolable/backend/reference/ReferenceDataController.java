package com.schoolable.backend.reference;

import com.schoolable.backend.config.FeatureFlags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/reference-data")
@Tag(name = "Reference Data")
public class ReferenceDataController {

    private final FeatureFlags featureFlags;

    public ReferenceDataController(FeatureFlags featureFlags) {
        this.featureFlags = featureFlags;
    }

    @Operation(summary = "Get reference data for dashboards and mobile")
    @GetMapping
    public ResponseEntity<?> getReferenceData(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        List<Map<String, String>> taskStatuses = List.of(
            Map.of("value", "TODO", "label", "To Do"),
            Map.of("value", "IN_PROGRESS", "label", "In Progress"),
            Map.of("value", "REVIEW", "label", "Review"),
            Map.of("value", "DONE", "label", "Done"),
            Map.of("value", "CANCELLED", "label", "Cancelled")
        );

        List<Map<String, String>> taskStatusFilters = new ArrayList<>();
        taskStatusFilters.add(Map.of("value", "All", "label", "All"));
        taskStatusFilters.addAll(taskStatuses);
        taskStatusFilters.add(Map.of("value", "Overdue", "label", "Overdue"));

        List<Map<String, String>> taskPriorities = List.of(
            Map.of("value", "Low", "label", "Low"),
            Map.of("value", "Medium", "label", "Medium"),
            Map.of("value", "High", "label", "High"),
            Map.of("value", "Critical", "label", "Critical")
        );

        List<Map<String, String>> taskPriorityFilters = new ArrayList<>();
        taskPriorityFilters.add(Map.of("value", "All", "label", "All"));
        taskPriorityFilters.addAll(taskPriorities);

        List<String> daysOfWeek = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            daysOfWeek.add(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("featureFlags", Map.of(
            "messagingEnabled", featureFlags.isMessagingEnabled()
        ));
        response.put("taskStatuses", taskStatuses);
        response.put("taskStatusFilters", taskStatusFilters);
        response.put("taskPriorities", taskPriorities);
        response.put("taskPriorityFilters", taskPriorityFilters);
        response.put("kpiProgressSources", List.of(
            Map.of("value", "TASKS_COMPLETED", "label", "Tasks Completed"),
            Map.of("value", "DAILY_REPORT_COUNT", "label", "Daily Reports Submitted"),
            Map.of("value", "DAILY_REPORT_SCORE_AVG", "label", "Daily Report Avg Score")
        ));
        response.put("weeklyReportCriteria", List.of(
            Map.of(
                "key", "initiative",
                "name", "Initiative & Proactiveness",
                "description", "Takes ownership, suggests improvements, works without constant direction."
            ),
            Map.of(
                "key", "attitude",
                "name", "Attitude & Mindset",
                "description", "Positive approach to work, handles challenges well, remains positive."
            ),
            Map.of(
                "key", "teamwork",
                "name", "Teamwork & Collaboration",
                "description", "Communication quality, helps teammates, professional conduct."
            )
        ));
        response.put("peerFeedbackCriteria", Map.of(
            "peer", List.of(
                Map.of(
                    "key", "supportRating",
                    "name", "Support & Helpfulness",
                    "description", "How supportive and helpful is this colleague when you need assistance?"
                ),
                Map.of(
                    "key", "collaborationRating",
                    "name", "Collaboration",
                    "description", "How well do they work with others and contribute to team efforts?"
                ),
                Map.of(
                    "key", "adaptabilityRating",
                    "name", "Adaptability",
                    "description", "How well do they adapt to changes and new situations?"
                ),
                Map.of(
                    "key", "valuesRating",
                    "name", "Company Values",
                    "description", "How well do they embody and practice company values?"
                ),
                Map.of(
                    "key", "accountabilityRating",
                    "name", "Accountability",
                    "description", "How reliable are they in taking ownership of their work?"
                ),
                Map.of(
                    "key", "feedbackRating",
                    "name", "Openness to Feedback",
                    "description", "How receptive are they to constructive feedback?"
                )
            ),
            "leadership", List.of(
                Map.of(
                    "key", "orgGuidanceRating",
                    "name", "Organizational Guidance",
                    "description", "How well do they provide direction and guidance to the team?"
                ),
                Map.of(
                    "key", "peopleCultureRating",
                    "name", "People & Culture Leadership",
                    "description", "How well do they foster a positive team culture?"
                ),
                Map.of(
                    "key", "influenceRating",
                    "name", "Leadership Influence",
                    "description", "How effectively do they inspire and motivate the team?"
                )
            )
        ));
        response.put("performanceCriteria", List.of(
            Map.of(
                "id", "adaptability",
                "name", "Adaptability",
                "description", "How well does the employee respond to change and adjust to new situations?",
                "pillar", "behavioral",
                "forTeamLeadsOnly", false
            ),
            Map.of(
                "id", "initiative",
                "name", "Initiative",
                "description", "Does the employee proactively identify and solve problems without being asked?",
                "pillar", "behavioral",
                "forTeamLeadsOnly", false
            ),
            Map.of(
                "id", "company_values",
                "name", "Company Values Alignment",
                "description", "How well does the employee embody and promote company core values?",
                "pillar", "culture_fit",
                "forTeamLeadsOnly", false
            ),
            Map.of(
                "id", "work_ethics",
                "name", "Work Ethics & Integrity",
                "description", "Is the employee honest, trustworthy, and ethical in their work?",
                "pillar", "culture_fit",
                "forTeamLeadsOnly", false
            ),
            Map.of(
                "id", "skill_application",
                "name", "Application of New Skills",
                "description", "Has the employee effectively applied newly learned skills in their work?",
                "pillar", "growth",
                "forTeamLeadsOnly", false
            ),
            Map.of(
                "id", "feedback_receptiveness",
                "name", "Openness to Feedback",
                "description", "How well does the employee receive and act on constructive feedback?",
                "pillar", "growth",
                "forTeamLeadsOnly", false
            ),
            Map.of(
                "id", "decision_making",
                "name", "Executive Decision-Making",
                "description", "Quality and impact of decisions made as a leader",
                "pillar", "leadership",
                "forTeamLeadsOnly", true
            ),
            Map.of(
                "id", "people_leadership",
                "name", "People & Culture Leadership",
                "description", "Ability to inspire, develop, and support team members",
                "pillar", "leadership",
                "forTeamLeadsOnly", true
            ),
            Map.of(
                "id", "crisis_handling",
                "name", "Crisis/Conflict Handling",
                "description", "Effectiveness in resolving conflicts and managing crises",
                "pillar", "leadership",
                "forTeamLeadsOnly", true
            )
        ));
        response.put("compliancePolicyTypes", List.of(
            Map.of("value", "policy", "label", "Policy Acknowledgement"),
            Map.of("value", "upload", "label", "Document Upload"),
            Map.of("value", "training", "label", "Training Completion")
        ));
        response.put("reportTypes", List.of(
            Map.of("value", "All", "label", "All"),
            Map.of("value", "Department", "label", "Department"),
            Map.of("value", "Performance", "label", "Performance"),
            Map.of("value", "Compliance", "label", "Compliance")
        ));
        response.put("auditEntityTypes", List.of(
            "All",
            "TASK",
            "PROFILE",
            "ANNOUNCEMENT",
            "COMPLIANCE",
            "ATTENDANCE"
        ));
        response.put("smartReminderTypes", List.of(
            Map.of("value", "check_in", "label", "Check-in"),
            Map.of("value", "task_due", "label", "Task Due"),
            Map.of("value", "report_submission", "label", "Report"),
            Map.of("value", "peer_feedback", "label", "Feedback"),
            Map.of("value", "aura_penalty", "label", "Aura Penalty"),
            Map.of("value", "custom", "label", "Custom")
        ));
        response.put("smartReminderChannels", List.of("push", "email", "sms"));
        response.put("smartReminderTargets", List.of(
            Map.of("value", "pending_only", "label", "Pending only"),
            Map.of("value", "all", "label", "All staff"),
            Map.of("value", "specific_team", "label", "Specific team"),
            Map.of("value", "specific_users", "label", "Specific users")
        ));
        response.put("daysOfWeek", daysOfWeek);
        response.put("genders", List.of("Male", "Female"));
        response.put("attendanceLateReasons", List.of(
            "Traffic congestion",
            "Public transport delay",
            "Family emergency",
            "Health issue",
            "Weather conditions",
            "Other"
        ));

        return ResponseEntity.ok(response);
    }
}
