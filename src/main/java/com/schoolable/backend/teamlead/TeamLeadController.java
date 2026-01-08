package com.schoolable.backend.teamlead;

import com.schoolable.backend.performance.AuraDashboardDto.EmployeeAuraResponse;
import com.schoolable.backend.performance.AuraDashboardService;
import com.schoolable.backend.performance.PeerHelpfulnessRating;
import com.schoolable.backend.performance.PeerHelpfulnessRepository;
import com.schoolable.backend.performance.WeeklyReportRepository;
import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import com.schoolable.backend.task.TaskRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for Team Lead specific dashboard data.
 * 
 * Endpoints:
 * - GET /api/team-lead/dashboard-stats - Dashboard KPIs and metrics
 * - GET /api/team-lead/team-members - Team members with Aura scores
 * - GET /api/team-lead/weekly-report-status - Check if reports submitted this week
 */
@RestController
@RequestMapping("/api/team-lead")
@CrossOrigin(origins = "*")
@Tag(name = "Team Lead", description = "Team Lead dashboard APIs")
public class TeamLeadController {

    private final ProfileRepository profileRepository;
    private final TaskRepository taskRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final AuraDashboardService auraDashboardService;
    private final PeerHelpfulnessRepository peerHelpfulnessRepository;

    private final com.schoolable.backend.kpi.TeamAiInsightsService teamAiInsightsService;

    public TeamLeadController(
            ProfileRepository profileRepository,
            TaskRepository taskRepository,
            WeeklyReportRepository weeklyReportRepository,
            AuraDashboardService auraDashboardService,
            PeerHelpfulnessRepository peerHelpfulnessRepository,
            com.schoolable.backend.kpi.TeamAiInsightsService teamAiInsightsService) {
        this.profileRepository = profileRepository;
        this.taskRepository = taskRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.auraDashboardService = auraDashboardService;
        this.peerHelpfulnessRepository = peerHelpfulnessRepository;
        this.teamAiInsightsService = teamAiInsightsService;
    }

    /**
     * Get AI-generated strategic insights for the team.
     */
    @Operation(summary = "Get AI Team Insights")
    @GetMapping("/ai-insights")
    public ResponseEntity<?> getTeamAiInsights(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }

        try {
            UUID teamLeadId = (UUID) auth.getPrincipal();
            Map<String, Object> insights = teamAiInsightsService.generateTeamInsights(teamLeadId);
            
            if (insights.containsKey("error")) {
                return ResponseEntity.badRequest().body(insights); 
            }
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
             return ResponseEntity.status(500).body(Map.of("error", "Failed to generate team insights: " + e.getMessage()));
        }
    }

    /**
     * Get dashboard statistics for the team lead.
     * 
     * Returns:
     * - Team size
     * - Tasks completed this month
     * - Tasks in progress
     * - Weekly report submission status
     * - Team average Aura score
     */
    @Operation(summary = "Get Team Lead dashboard statistics")
    @GetMapping("/dashboard-stats")
    public ResponseEntity<?> getDashboardStats(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        try {
            UUID teamLeadId = (UUID) auth.getPrincipal();
            
            // Get team lead profile
            var profileOpt = profileRepository.findById(teamLeadId);
            if (profileOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
            }
            
            Profile teamLead = profileOpt.get();
            
            // Verify user is a team lead
            if (!Boolean.TRUE.equals(teamLead.getIsTeamLead()) && !"admin".equalsIgnoreCase(teamLead.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied. Team Lead role required."));
            }
            
            String department = teamLead.getDepartment();
            if (department == null || department.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Team lead does not belong to a department"));
            }
            
            // Get active team members (same department, INCLUDING the team lead)
            // Include members with active, pending, or unset status
            List<Profile> allDepartmentMembers = profileRepository.findByDepartment(department)
                    .stream()
                    .filter(p -> {
                        String status = p.getStatus();
                        // Include if status is active, pending, probation, or null/empty
                        return status == null || status.isEmpty() || 
                               "active".equalsIgnoreCase(status) || 
                               "pending".equalsIgnoreCase(status) ||
                               "probation".equalsIgnoreCase(status);
                    })
                    .collect(Collectors.toList());
            
            // Team size includes all members including team lead
            int teamSize = allDepartmentMembers.size();
            
            // Get all member IDs including team lead for task counting
            Set<UUID> memberIds = allDepartmentMembers.stream().map(Profile::getId).collect(Collectors.toSet());
            
            // Team members list excludes team lead (for display purposes like weekly reports)
            List<Profile> teamMembers = allDepartmentMembers.stream()
                    .filter(p -> !p.getId().equals(teamLeadId))
                    .collect(Collectors.toList());
            
            // Get task statistics for the team
            // Filter tasks by either assignee being a team member OR organization matching the department
            List<com.schoolable.backend.task.Task> allTasks = taskRepository.findAll();
            
            // Filter tasks that belong to this team (by assignee OR by organization)
            List<com.schoolable.backend.task.Task> teamTasks = allTasks.stream()
                    .filter(t -> memberIds.contains(t.getAssigneeId()) || 
                                 (t.getOrganization() != null && t.getOrganization().equalsIgnoreCase(department)))
                    .collect(Collectors.toList());
            
            long tasksCompleted = teamTasks.stream()
                    .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()) || "Done".equalsIgnoreCase(t.getStatus()))
                    .count();
            
            long tasksInProgress = teamTasks.stream()
                    .filter(t -> "In Progress".equalsIgnoreCase(t.getStatus()))
                    .count();
            
            long tasksPending = teamTasks.stream()
                    .filter(t -> "Pending".equalsIgnoreCase(t.getStatus()) || "To Do".equalsIgnoreCase(t.getStatus()))
                    .count();
            
            // Check weekly report status
            LocalDate now = LocalDate.now();
            int currentWeek = now.get(WeekFields.ISO.weekOfYear());
            int currentYear = now.getYear();
            
            long reportsSubmittedThisWeek = weeklyReportRepository.findByReviewerIdAndWeekNumberAndYear(
                    teamLeadId, currentWeek, currentYear).size();
            
            // Reports are only submitted for team members (excluding team lead)
            int reportsRequired = teamMembers.size();
            boolean weeklyReportsComplete = reportsSubmittedThisWeek >= reportsRequired && reportsRequired > 0;
            
            // Calculate average team Aura score (0-100 scale percentage)
            // Include ALL department members including team lead for Aura calculation
            double totalAura = 0;
            int membersWithAura = 0;
            for (Profile member : allDepartmentMembers) {
                try {
                    EmployeeAuraResponse auraData = auraDashboardService.getEmployeeAuraDashboard(member.getId());
                    if (auraData != null && auraData.getAuraScore() != null) {
                        totalAura += auraData.getAuraScore(); // Keep as 100-scale percentage
                        membersWithAura++;
                    }
                } catch (Exception ignored) {
                    // Skip members without Aura data
                }
            }
            double averageAura = membersWithAura > 0 ? totalAura / membersWithAura : 0;
            
            // Build response
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("team_lead_id", teamLeadId);
            stats.put("team_lead_name", teamLead.getFullName());
            stats.put("department", department);
            stats.put("team_size", teamSize);
            
            // Task metrics
            Map<String, Object> tasks = new LinkedHashMap<>();
            tasks.put("completed", tasksCompleted);
            tasks.put("in_progress", tasksInProgress);
            tasks.put("pending", tasksPending);
            tasks.put("total", tasksCompleted + tasksInProgress + tasksPending);
            stats.put("tasks", tasks);
            
            // Weekly report status
            Map<String, Object> weeklyStatus = new LinkedHashMap<>();
            weeklyStatus.put("current_week", currentWeek);
            weeklyStatus.put("year", currentYear);
            weeklyStatus.put("reports_submitted", reportsSubmittedThisWeek);
            weeklyStatus.put("reports_required", reportsRequired);
            weeklyStatus.put("is_complete", weeklyReportsComplete);
            stats.put("weekly_reports", weeklyStatus);
            
            // Team performance
            Map<String, Object> performance = new LinkedHashMap<>();
            performance.put("average_aura_score", Math.round(averageAura * 10) / 10.0);
            performance.put("members_with_aura_data", membersWithAura);
            stats.put("team_performance", performance);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch dashboard stats: " + e.getMessage()));
        }
    }

    /**
     * Get team members with their Aura scores and status.
     * 
     * Returns list of team members with:
     * - Profile info
     * - Current Aura score
     * - Pillar breakdown
     * - Weekly report status
     */
    @Operation(summary = "Get team members with Aura scores")
    @GetMapping("/team-members")
    public ResponseEntity<?> getTeamMembers(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "true") boolean includeSelf) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        try {
            UUID teamLeadId = (UUID) auth.getPrincipal();
            
            // Get team lead profile
            var profileOpt = profileRepository.findById(teamLeadId);
            if (profileOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
            }
            
            Profile teamLead = profileOpt.get();
            
            // Verify user is a team lead
            if (!Boolean.TRUE.equals(teamLead.getIsTeamLead()) && !"admin".equalsIgnoreCase(teamLead.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied. Team Lead role required."));
            }
            
            String department = teamLead.getDepartment();
            if (department == null || department.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Team lead does not belong to a department"));
            }
            
            // Get current week info
            LocalDate now = LocalDate.now();
            int currentWeek = now.get(WeekFields.ISO.weekOfYear());
            int currentYear = now.getYear();
            
            // Get team members (same department, optionally including self)
            // Include members with active, pending, or unset status
            List<Profile> teamMembers = profileRepository.findByDepartment(department)
                    .stream()
                    .filter(p -> includeSelf || !p.getId().equals(teamLeadId))
                    .filter(p -> {
                        String status = p.getStatus();
                        return status == null || status.isEmpty() || 
                               "active".equalsIgnoreCase(status) || 
                               "pending".equalsIgnoreCase(status) ||
                               "probation".equalsIgnoreCase(status);
                    })
                    .collect(Collectors.toList());
            
            // Build response for each team member
            List<Map<String, Object>> membersData = new ArrayList<>();
            
            for (Profile member : teamMembers) {
                Map<String, Object> memberInfo = new LinkedHashMap<>();
                
                // Basic profile info
                memberInfo.put("id", member.getId());
                memberInfo.put("full_name", member.getFullName());
                memberInfo.put("email", member.getEmail());
                memberInfo.put("job_title", member.getJobTitle());
                memberInfo.put("department", member.getDepartment());
                memberInfo.put("status", member.getStatus());
                memberInfo.put("employee_id", member.getEmployeeId());
                memberInfo.put("is_team_lead", Boolean.TRUE.equals(member.getIsTeamLead()));
                
                // Generate avatar URL matching mobile app logic (gender-based)
                String avatarUrl = member.getAvatarUrl();
                if (avatarUrl == null || avatarUrl.isEmpty()) {
                    String seed = member.getEmployeeId() != null ? member.getEmployeeId() : 
                                  member.getEmail() != null ? member.getEmail() : "User";
                    String gender = member.getGender();
                    String style = "bottts"; // Default for unspecified
                    if ("male".equalsIgnoreCase(gender)) {
                        style = "adventurer";
                    } else if ("female".equalsIgnoreCase(gender)) {
                        style = "adventurer-neutral";
                    }
                    avatarUrl = "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
                }
                memberInfo.put("avatar_url", avatarUrl);
                
                // Aura score and pillars
                try {
                    EmployeeAuraResponse auraData = auraDashboardService.getEmployeeAuraDashboard(member.getId());
                    if (auraData != null) {
                        memberInfo.put("aura_score", auraData.getAuraScore());
                        memberInfo.put("aura_grade", auraData.getGrade());
                        // Return simplified pillars structure for frontend compatibility
                        if (auraData.getPillars() != null) {
                            Map<String, Number> simplePillars = new LinkedHashMap<>();
                            var pillars = auraData.getPillars();
                            simplePillars.put("technical", pillars.getTechnical() != null ? pillars.getTechnical().getScore() : 0);
                            simplePillars.put("behavioral", pillars.getBehavioral() != null ? pillars.getBehavioral().getScore() : 0);
                            simplePillars.put("culture", pillars.getCultureFit() != null ? pillars.getCultureFit().getScore() : 0);
                            simplePillars.put("growth", pillars.getGrowthLearning() != null ? pillars.getGrowthLearning().getScore() : 0);
                            memberInfo.put("pillars", simplePillars);
                        } else {
                            memberInfo.put("pillars", null);
                        }
                    } else {
                        memberInfo.put("aura_score", null);
                        memberInfo.put("aura_grade", "N/A");
                        memberInfo.put("pillars", null);
                    }
                } catch (Exception e) {
                    memberInfo.put("aura_score", null);
                    memberInfo.put("aura_grade", "N/A");
                    memberInfo.put("pillars", null);
                }
                
                // Check if weekly report submitted for this member
                boolean hasWeeklyReport = weeklyReportRepository.findByEmployeeIdAndWeekNumberAndYear(
                        member.getId(), currentWeek, currentYear).isPresent();
                memberInfo.put("weekly_report_submitted", hasWeeklyReport);
                
                membersData.add(memberInfo);
            }
            
            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("team_lead", teamLead.getFullName());
            response.put("department", department);
            response.put("current_week", currentWeek);
            response.put("year", currentYear);
            response.put("member_count", membersData.size());
            response.put("members", membersData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch team members: " + e.getMessage()));
        }
    }

    /**
     * Check weekly report submission status for current week.
     */
    @Operation(summary = "Check weekly report submission status")
    @GetMapping("/weekly-report-status")
    public ResponseEntity<?> getWeeklyReportStatus(
            Authentication auth,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Integer year) {
        
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        try {
            UUID teamLeadId = (UUID) auth.getPrincipal();
            
            // Get team lead profile
            var profileOpt = profileRepository.findById(teamLeadId);
            if (profileOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
            }
            
            Profile teamLead = profileOpt.get();
            
            // Verify user is a team lead
            if (!Boolean.TRUE.equals(teamLead.getIsTeamLead()) && !"admin".equalsIgnoreCase(teamLead.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied. Team Lead role required."));
            }
            
            String department = teamLead.getDepartment();
            if (department == null || department.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Team lead does not belong to a department"));
            }
            
            // Default to current week
            LocalDate now = LocalDate.now();
            int targetWeek = week != null ? week : now.get(WeekFields.ISO.weekOfYear());
            int targetYear = year != null ? year : now.getYear();
            
            // Get team members
            List<Profile> teamMembers = profileRepository.findByDepartment(department)
                    .stream()
                    .filter(p -> !p.getId().equals(teamLeadId))
                    .collect(Collectors.toList());
            
            // Check each team member's report status
            List<Map<String, Object>> memberStatuses = new ArrayList<>();
            int submittedCount = 0;
            
            for (Profile member : teamMembers) {
                boolean hasReport = weeklyReportRepository.findByEmployeeIdAndWeekNumberAndYear(
                        member.getId(), targetWeek, targetYear).isPresent();
                
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("employee_id", member.getId());
                status.put("full_name", member.getFullName());
                status.put("submitted", hasReport);
                memberStatuses.add(status);
                
                if (hasReport) submittedCount++;
            }
            
            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("team_lead_id", teamLeadId);
            response.put("week", targetWeek);
            response.put("year", targetYear);
            response.put("team_size", teamMembers.size());
            response.put("submitted_count", submittedCount);
            response.put("pending_count", teamMembers.size() - submittedCount);
            response.put("is_complete", submittedCount == teamMembers.size() && teamMembers.size() > 0);
            response.put("members", memberStatuses);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch status: " + e.getMessage()));
        }
    }

    /**
     * Get peer feedback status for the team.
     * 
     * Returns:
     * - List of team members with their peer feedback submission status
     * - Aggregated anonymous scores from peers
     */
    @Operation(summary = "Get team peer feedback status")
    @GetMapping("/peer-feedback-status")
    public ResponseEntity<?> getPeerFeedbackStatus(
            Authentication auth,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Integer year) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        
        try {
            UUID teamLeadId = (UUID) auth.getPrincipal();
            
            // Get team lead profile
            var profileOpt = profileRepository.findById(teamLeadId);
            if (profileOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Profile not found"));
            }
            Profile teamLead = profileOpt.get();
            
            // Determine week/year
            LocalDate now = LocalDate.now();
            int targetWeek = week != null ? week : now.get(WeekFields.ISO.weekOfWeekBasedYear());
            int targetYear = year != null ? year : now.getYear();
            
            // Get team members
            List<Profile> teamMembers = profileRepository.findByTeamLeadId(teamLeadId);
            if (teamMembers.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "team_size", 0,
                    "submitted_count", 0,
                    "pending_count", 0,
                    "completion_rate", 0,
                    "week", targetWeek,
                    "year", targetYear,
                    "members", List.of()
                ));
            }
            
            List<Map<String, Object>> memberStatuses = new ArrayList<>();
            int submittedCount = 0;
            
            for (Profile member : teamMembers) {
                // Check if this member has submitted peer ratings for this week
                long ratingsGiven = peerHelpfulnessRepository.countByRaterIdAndWeekNumberAndYear(
                        member.getId(), targetWeek, targetYear);
                boolean hasSubmitted = ratingsGiven > 0;
                
                // Get ratings received for this week (to show aggregated scores)
                List<PeerHelpfulnessRating> ratingsReceived = peerHelpfulnessRepository
                        .findByRatedUserIdAndWeekNumberAndYear(member.getId(), targetWeek, targetYear);
                
                // Calculate average score received
                Double avgScoreReceived = ratingsReceived.isEmpty() ? null : 
                        ratingsReceived.stream()
                                .mapToDouble(PeerHelpfulnessRating::getRating)
                                .average()
                                .orElse(0.0);
                
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("id", member.getId());
                status.put("full_name", member.getFullName());
                status.put("job_title", member.getJobTitle());
                status.put("department", member.getDepartment());
                status.put("avatar_url", member.getAvatarUrl());
                status.put("has_submitted_feedback", hasSubmitted);
                status.put("feedback_received_count", ratingsReceived.size());
                
                if (avgScoreReceived != null) {
                    // Create aggregated scores object (anonymous)
                    Map<String, Object> aggregatedScores = new LinkedHashMap<>();
                    aggregatedScores.put("overall", Math.round(avgScoreReceived * 10.0) / 10.0);
                    aggregatedScores.put("support", Math.round(avgScoreReceived * 10.0) / 10.0); // Same for now
                    aggregatedScores.put("collaboration", Math.round(avgScoreReceived * 10.0) / 10.0);
                    aggregatedScores.put("adaptability", Math.round(avgScoreReceived * 10.0) / 10.0);
                    aggregatedScores.put("values", Math.round(avgScoreReceived * 10.0) / 10.0);
                    aggregatedScores.put("accountability", Math.round(avgScoreReceived * 10.0) / 10.0);
                    aggregatedScores.put("feedback_openness", Math.round(avgScoreReceived * 10.0) / 10.0);
                    status.put("aggregated_scores", aggregatedScores);
                }
                
                memberStatuses.add(status);
                if (hasSubmitted) submittedCount++;
            }
            
            int teamSize = teamMembers.size();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("team_lead_id", teamLeadId);
            response.put("week", targetWeek);
            response.put("year", targetYear);
            response.put("team_size", teamSize);
            response.put("submitted_count", submittedCount);
            response.put("pending_count", teamSize - submittedCount);
            response.put("completion_rate", teamSize > 0 ? Math.round((submittedCount * 100.0) / teamSize) : 0);
            response.put("members", memberStatuses);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch peer feedback status: " + e.getMessage()));
        }
    }
}
