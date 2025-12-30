package com.schoolable.backend.performance;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs for Weekly Performance Report API
 */
public class WeeklyReportDto {

    /**
     * Request DTO for submitting weekly report for a single team member
     */
    public static class SingleReportRequest {
        
        @NotNull(message = "Employee ID is required")
        private String employeeId;

        @NotNull(message = "Week number is required")
        @Min(value = 1, message = "Week number must be between 1 and 53")
        @Max(value = 53, message = "Week number must be between 1 and 53")
        private Integer weekNumber;

        @NotNull(message = "Year is required")
        private Integer year;

        // 4 Core Pillar Scores (1-5)
        @NotNull @Min(1) @Max(5)
        private Integer technicalScore;

        @NotNull @Min(1) @Max(5)
        private Integer behavioralScore;

        @NotNull @Min(1) @Max(5)
        private Integer cultureFitScore;

        @NotNull @Min(1) @Max(5)
        private Integer growthLearningScore;

        // Notes
        private String technicalNotes;
        private String behavioralNotes;
        private String cultureFitNotes;
        private String growthLearningNotes;

        // Summary
        private String weeklyHighlights;
        private String areasForFocus;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public Integer getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(Integer technicalScore) { this.technicalScore = technicalScore; }

        public Integer getBehavioralScore() { return behavioralScore; }
        public void setBehavioralScore(Integer behavioralScore) { this.behavioralScore = behavioralScore; }

        public Integer getCultureFitScore() { return cultureFitScore; }
        public void setCultureFitScore(Integer cultureFitScore) { this.cultureFitScore = cultureFitScore; }

        public Integer getGrowthLearningScore() { return growthLearningScore; }
        public void setGrowthLearningScore(Integer growthLearningScore) { this.growthLearningScore = growthLearningScore; }

        public String getTechnicalNotes() { return technicalNotes; }
        public void setTechnicalNotes(String technicalNotes) { this.technicalNotes = technicalNotes; }

        public String getBehavioralNotes() { return behavioralNotes; }
        public void setBehavioralNotes(String behavioralNotes) { this.behavioralNotes = behavioralNotes; }

        public String getCultureFitNotes() { return cultureFitNotes; }
        public void setCultureFitNotes(String cultureFitNotes) { this.cultureFitNotes = cultureFitNotes; }

        public String getGrowthLearningNotes() { return growthLearningNotes; }
        public void setGrowthLearningNotes(String growthLearningNotes) { this.growthLearningNotes = growthLearningNotes; }

        public String getWeeklyHighlights() { return weeklyHighlights; }
        public void setWeeklyHighlights(String weeklyHighlights) { this.weeklyHighlights = weeklyHighlights; }

        public String getAreasForFocus() { return areasForFocus; }
        public void setAreasForFocus(String areasForFocus) { this.areasForFocus = areasForFocus; }
    }

    /**
     * Request DTO for batch submission (all team members for a week)
     */
    public static class BatchReportRequest {

        @NotNull(message = "Week number is required")
        @Min(1) @Max(53)
        private Integer weekNumber;

        @NotNull(message = "Year is required")
        private Integer year;

        @NotEmpty(message = "At least one report is required")
        private List<TeamMemberWeeklyReport> reports;

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public List<TeamMemberWeeklyReport> getReports() { return reports; }
        public void setReports(List<TeamMemberWeeklyReport> reports) { this.reports = reports; }
    }

    /**
     * Individual team member report within batch
     */
    public static class TeamMemberWeeklyReport {

        @NotNull private String employeeId;
        private String employeeName;

        @NotNull @Min(1) @Max(5) private Integer technicalScore;
        @NotNull @Min(1) @Max(5) private Integer behavioralScore;
        @NotNull @Min(1) @Max(5) private Integer cultureFitScore;
        @NotNull @Min(1) @Max(5) private Integer growthLearningScore;

        private String technicalNotes;
        private String behavioralNotes;
        private String cultureFitNotes;
        private String growthLearningNotes;
        private String weeklyHighlights;
        private String areasForFocus;

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public Integer getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(Integer technicalScore) { this.technicalScore = technicalScore; }

        public Integer getBehavioralScore() { return behavioralScore; }
        public void setBehavioralScore(Integer behavioralScore) { this.behavioralScore = behavioralScore; }

        public Integer getCultureFitScore() { return cultureFitScore; }
        public void setCultureFitScore(Integer cultureFitScore) { this.cultureFitScore = cultureFitScore; }

        public Integer getGrowthLearningScore() { return growthLearningScore; }
        public void setGrowthLearningScore(Integer growthLearningScore) { this.growthLearningScore = growthLearningScore; }

        public String getTechnicalNotes() { return technicalNotes; }
        public void setTechnicalNotes(String technicalNotes) { this.technicalNotes = technicalNotes; }

        public String getBehavioralNotes() { return behavioralNotes; }
        public void setBehavioralNotes(String behavioralNotes) { this.behavioralNotes = behavioralNotes; }

        public String getCultureFitNotes() { return cultureFitNotes; }
        public void setCultureFitNotes(String cultureFitNotes) { this.cultureFitNotes = cultureFitNotes; }

        public String getGrowthLearningNotes() { return growthLearningNotes; }
        public void setGrowthLearningNotes(String growthLearningNotes) { this.growthLearningNotes = growthLearningNotes; }

        public String getWeeklyHighlights() { return weeklyHighlights; }
        public void setWeeklyHighlights(String weeklyHighlights) { this.weeklyHighlights = weeklyHighlights; }

        public String getAreasForFocus() { return areasForFocus; }
        public void setAreasForFocus(String areasForFocus) { this.areasForFocus = areasForFocus; }
    }

    /**
     * Response DTO for weekly report
     */
    public static class ReportResponse {
        private Long id;
        private String employeeId;
        private String employeeName;
        private String department;
        private Integer weekNumber;
        private Integer year;
        private String weekStartDate;
        private String weekEndDate;

        // Scores (1-5)
        private Integer technicalScore;
        private Integer behavioralScore;
        private Integer cultureFitScore;
        private Integer growthLearningScore;

        // Scores as percentages (for display)
        private Integer technicalPct;
        private Integer behavioralPct;
        private Integer cultureFitPct;
        private Integer growthLearningPct;

        // Calculated
        private Double weeklyAura;
        private String grade;

        // Notes
        private String technicalNotes;
        private String behavioralNotes;
        private String cultureFitNotes;
        private String growthLearningNotes;
        private String weeklyHighlights;
        private String areasForFocus;

        // Metadata
        private String reviewerName;
        private String createdAt;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public String getWeekStartDate() { return weekStartDate; }
        public void setWeekStartDate(String weekStartDate) { this.weekStartDate = weekStartDate; }

        public String getWeekEndDate() { return weekEndDate; }
        public void setWeekEndDate(String weekEndDate) { this.weekEndDate = weekEndDate; }

        public Integer getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(Integer technicalScore) { this.technicalScore = technicalScore; }

        public Integer getBehavioralScore() { return behavioralScore; }
        public void setBehavioralScore(Integer behavioralScore) { this.behavioralScore = behavioralScore; }

        public Integer getCultureFitScore() { return cultureFitScore; }
        public void setCultureFitScore(Integer cultureFitScore) { this.cultureFitScore = cultureFitScore; }

        public Integer getGrowthLearningScore() { return growthLearningScore; }
        public void setGrowthLearningScore(Integer growthLearningScore) { this.growthLearningScore = growthLearningScore; }

        public Integer getTechnicalPct() { return technicalPct; }
        public void setTechnicalPct(Integer technicalPct) { this.technicalPct = technicalPct; }

        public Integer getBehavioralPct() { return behavioralPct; }
        public void setBehavioralPct(Integer behavioralPct) { this.behavioralPct = behavioralPct; }

        public Integer getCultureFitPct() { return cultureFitPct; }
        public void setCultureFitPct(Integer cultureFitPct) { this.cultureFitPct = cultureFitPct; }

        public Integer getGrowthLearningPct() { return growthLearningPct; }
        public void setGrowthLearningPct(Integer growthLearningPct) { this.growthLearningPct = growthLearningPct; }

        public Double getWeeklyAura() { return weeklyAura; }
        public void setWeeklyAura(Double weeklyAura) { this.weeklyAura = weeklyAura; }

        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }

        public String getTechnicalNotes() { return technicalNotes; }
        public void setTechnicalNotes(String technicalNotes) { this.technicalNotes = technicalNotes; }

        public String getBehavioralNotes() { return behavioralNotes; }
        public void setBehavioralNotes(String behavioralNotes) { this.behavioralNotes = behavioralNotes; }

        public String getCultureFitNotes() { return cultureFitNotes; }
        public void setCultureFitNotes(String cultureFitNotes) { this.cultureFitNotes = cultureFitNotes; }

        public String getGrowthLearningNotes() { return growthLearningNotes; }
        public void setGrowthLearningNotes(String growthLearningNotes) { this.growthLearningNotes = growthLearningNotes; }

        public String getWeeklyHighlights() { return weeklyHighlights; }
        public void setWeeklyHighlights(String weeklyHighlights) { this.weeklyHighlights = weeklyHighlights; }

        public String getAreasForFocus() { return areasForFocus; }
        public void setAreasForFocus(String areasForFocus) { this.areasForFocus = areasForFocus; }

        public String getReviewerName() { return reviewerName; }
        public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    /**
     * Weekly summary for admin dashboard
     */
    public static class WeeklySummary {
        private Integer weekNumber;
        private Integer year;
        private String weekStartDate;
        private String weekEndDate;
        private Long totalReports;
        private Double averageAura;
        private Long teamsReported;

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public String getWeekStartDate() { return weekStartDate; }
        public void setWeekStartDate(String weekStartDate) { this.weekStartDate = weekStartDate; }

        public String getWeekEndDate() { return weekEndDate; }
        public void setWeekEndDate(String weekEndDate) { this.weekEndDate = weekEndDate; }

        public Long getTotalReports() { return totalReports; }
        public void setTotalReports(Long totalReports) { this.totalReports = totalReports; }

        public Double getAverageAura() { return averageAura; }
        public void setAverageAura(Double averageAura) { this.averageAura = averageAura; }

        public Long getTeamsReported() { return teamsReported; }
        public void setTeamsReported(Long teamsReported) { this.teamsReported = teamsReported; }
    }

    /**
     * SIMPLIFIED: Request for submitting the 3 team lead ratings only
     * Used by Team Lead dashboard
     */
    public static class SimplifiedRatingRequest {
        
        @NotNull(message = "Employee ID is required")
        private String employeeId;

        @NotNull(message = "Week number is required")
        @Min(1) @Max(53)
        private Integer weekNumber;

        @NotNull(message = "Year is required")
        private Integer year;

        // Only 3 ratings
        @NotNull @Min(1) @Max(5)
        private Integer teamworkCollaborationScore;

        @NotNull @Min(1) @Max(5)
        private Integer initiativeScore;

        @NotNull @Min(1) @Max(5)
        private Integer attitudeTowardsWorkScore;

        // Optional notes
        private String notes;

        // Getters & Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public Integer getTeamworkCollaborationScore() { return teamworkCollaborationScore; }
        public void setTeamworkCollaborationScore(Integer teamworkCollaborationScore) { this.teamworkCollaborationScore = teamworkCollaborationScore; }

        public Integer getInitiativeScore() { return initiativeScore; }
        public void setInitiativeScore(Integer initiativeScore) { this.initiativeScore = initiativeScore; }

        public Integer getAttitudeTowardsWorkScore() { return attitudeTowardsWorkScore; }
        public void setAttitudeTowardsWorkScore(Integer attitudeTowardsWorkScore) { this.attitudeTowardsWorkScore = attitudeTowardsWorkScore; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    /**
     * SIMPLIFIED: Batch request for all team members (3 ratings each)
     */
    public static class SimplifiedBatchRequest {
        
        @NotNull(message = "Week number is required")
        @Min(1) @Max(53)
        private Integer weekNumber;

        @NotNull(message = "Year is required")
        private Integer year;

        // Optional team report document URL
        private String teamReportUrl;

        @NotEmpty(message = "At least one report is required")
        private List<SimplifiedTeamMemberRating> ratings;

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public String getTeamReportUrl() { return teamReportUrl; }
        public void setTeamReportUrl(String teamReportUrl) { this.teamReportUrl = teamReportUrl; }

        public List<SimplifiedTeamMemberRating> getRatings() { return ratings; }
        public void setRatings(List<SimplifiedTeamMemberRating> ratings) { this.ratings = ratings; }
    }

    /**
     * Individual simplified rating within batch
     */
    public static class SimplifiedTeamMemberRating {
        
        @NotNull private String employeeId;
        private String employeeName;

        @NotNull @Min(1) @Max(5)
        private Integer teamworkCollaborationScore;

        @NotNull @Min(1) @Max(5)
        private Integer initiativeScore;

        @NotNull @Min(1) @Max(5)
        private Integer attitudeTowardsWorkScore;

        private String notes;

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public Integer getTeamworkCollaborationScore() { return teamworkCollaborationScore; }
        public void setTeamworkCollaborationScore(Integer score) { this.teamworkCollaborationScore = score; }

        public Integer getInitiativeScore() { return initiativeScore; }
        public void setInitiativeScore(Integer score) { this.initiativeScore = score; }

        public Integer getAttitudeTowardsWorkScore() { return attitudeTowardsWorkScore; }
        public void setAttitudeTowardsWorkScore(Integer score) { this.attitudeTowardsWorkScore = score; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}

