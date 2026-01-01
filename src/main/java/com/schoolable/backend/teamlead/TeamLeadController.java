package com.schoolable.backend.teamlead;

import com.schoolable.backend.performance.AuraDashboardService;
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

    public TeamLeadController(
            ProfileRepository profileRepository,
            TaskRepository taskRepository,
            WeeklyReportRepository weeklyReportRepository,
            AuraDashboardService auraDashboardService) {
        this.profileRepository = profileRepository;
        this.taskRepository = taskRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.auraDashboardService = auraDashboardService;
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
            
            // Get team members (same department, excluding self)
            List<Profile> teamMembers = profileRepository.findByDepartment(department)
                    .stream()
                    .filter(p -> !p.getId().equals(teamLeadId))
                    .collect(Collectors.toList());
            
            int teamSize = teamMembers.size();
            
            // Get task statistics for the team
            long tasksCompleted = taskRepository.findAll().stream()
                    .filter(t -> teamMembers.stream().anyMatch(m -> m.getId().equals(t.getAssigneeId())))
                    .filter(t -> "Completed".equalsIgnoreCase(t.getStatus()))
                    .count();
            
            long tasksInProgress = taskRepository.findAll().stream()
                    .filter(t -> teamMembers.stream().anyMatch(m -> m.getId().equals(t.getAssigneeId())))
                    .filter(t -> "In Progress".equalsIgnoreCase(t.getStatus()))
                    .count();
            
            long tasksPending = taskRepository.findAll().stream()
                    .filter(t -> teamMembers.stream().anyMatch(m -> m.getId().equals(t.getAssigneeId())))
                    .filter(t -> "Pending".equalsIgnoreCase(t.getStatus()) || "To Do".equalsIgnoreCase(t.getStatus()))
                    .count();
            
            // Check weekly report status
            LocalDate now = LocalDate.now();
            int currentWeek = now.get(WeekFields.ISO.weekOfYear());
            int currentYear = now.getYear();
            
            long reportsSubmittedThisWeek = weeklyReportRepository.findByReviewerIdAndWeekNumberAndYear(
                    teamLeadId, currentWeek, currentYear).size();
            
            boolean weeklyReportsComplete = reportsSubmittedThisWeek >= teamSize && teamSize > 0;
            
            // Calculate average team Aura score
            double totalAura = 0;
            int membersWithAura = 0;
            for (Profile member : teamMembers) {
                try {
                    Map<String, Object> auraData = auraDashboardService.getEmployeeAuraDashboard(member.getId());
                    if (auraData != null && auraData.get("overallScore") != null) {
                        totalAura += ((Number) auraData.get("overallScore")).doubleValue();
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
            weeklyStatus.put("reports_required", teamSize);
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
    public ResponseEntity<?> getTeamMembers(Authentication auth) {
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
            
            // Get team members (same department, excluding self)
            List<Profile> teamMembers = profileRepository.findByDepartment(department)
                    .stream()
                    .filter(p -> !p.getId().equals(teamLeadId))
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
                
                // Generate avatar URL
                String avatarUrl = member.getAvatarUrl();
                if (avatarUrl == null || avatarUrl.isEmpty()) {
                    String seed = member.getEmployeeId() != null ? member.getEmployeeId() : member.getEmail();
                    avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + seed;
                }
                memberInfo.put("avatar_url", avatarUrl);
                
                // Aura score and pillars
                try {
                    Map<String, Object> auraData = auraDashboardService.getEmployeeAuraDashboard(member.getId());
                    if (auraData != null) {
                        memberInfo.put("aura_score", auraData.get("overallScore"));
                        memberInfo.put("aura_grade", auraData.get("grade"));
                        memberInfo.put("pillars", auraData.get("pillars"));
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
}
