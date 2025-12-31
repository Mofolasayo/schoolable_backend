package com.schoolable.backend.performance;

import java.math.BigDecimal;

/**
 * DTOs for Employee Aura Dashboard API responses.
 * Used for mobile app and dashboard to display performance scores.
 */
public class AuraDashboardDto {

    /**
     * Main response for employee's Aura dashboard
     * GET /api/performance/my-aura
     */
    public static class EmployeeAuraResponse {
        private String employeeId;
        private String employeeName;
        private String department;
        private String role;
        
        // Overall Score
        private Double auraScore;        // 0-100
        private Double qgpa;             // 0-5.0
        private String grade;            // A, B, C, D, F
        
        // Pillar breakdown (0-100 each)
        private PillarScores pillars;
        
        // Metadata
        private Integer weeksRatedThisQuarter;
        private String currentQuarter;    // e.g., "Q4"
        private Integer currentYear;

        // Getters & Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public Double getAuraScore() { return auraScore; }
        public void setAuraScore(Double auraScore) { this.auraScore = auraScore; }

        public Double getQgpa() { return qgpa; }
        public void setQgpa(Double qgpa) { this.qgpa = qgpa; }

        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }

        public PillarScores getPillars() { return pillars; }
        public void setPillars(PillarScores pillars) { this.pillars = pillars; }

        public Integer getWeeksRatedThisQuarter() { return weeksRatedThisQuarter; }
        public void setWeeksRatedThisQuarter(Integer weeksRatedThisQuarter) { this.weeksRatedThisQuarter = weeksRatedThisQuarter; }

        public String getCurrentQuarter() { return currentQuarter; }
        public void setCurrentQuarter(String currentQuarter) { this.currentQuarter = currentQuarter; }

        public Integer getCurrentYear() { return currentYear; }
        public void setCurrentYear(Integer currentYear) { this.currentYear = currentYear; }
    }

    /**
     * Individual pillar score breakdown
     * Now 4 pillars at 25% each (collaboration merged into cultureFit)
     */
    public static class PillarScores {
        private PillarDetail technical;
        private PillarDetail behavioral;
        private PillarDetail cultureFit;      // Now includes collaboration metrics
        private PillarDetail growthLearning;

        public PillarDetail getTechnical() { return technical; }
        public void setTechnical(PillarDetail technical) { this.technical = technical; }

        public PillarDetail getBehavioral() { return behavioral; }
        public void setBehavioral(PillarDetail behavioral) { this.behavioral = behavioral; }

        public PillarDetail getCultureFit() { return cultureFit; }
        public void setCultureFit(PillarDetail cultureFit) { this.cultureFit = cultureFit; }

        public PillarDetail getGrowthLearning() { return growthLearning; }
        public void setGrowthLearning(PillarDetail growthLearning) { this.growthLearning = growthLearning; }
    }

    /**
     * Detailed breakdown of a single pillar
     */
    public static class PillarDetail {
        private String name;
        private Double score;           // 0-100
        private Double weight;          // e.g., 25.0 (25%)
        private Double contribution;    // score * weight / 100
        private String dataSource;      // "auto", "team_lead", "mixed"

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }

        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }

        public Double getContribution() { return contribution; }
        public void setContribution(Double contribution) { this.contribution = contribution; }

        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    }

    /**
     * Weekly trend data point for charts
     */
    public static class WeeklyTrendPoint {
        private Integer weekNumber;
        private Integer year;
        private String weekStartDate;
        private Double auraScore;
        private Double teamwork;
        private Double initiative;
        private Double attitude;

        public Integer getWeekNumber() { return weekNumber; }
        public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }

        public String getWeekStartDate() { return weekStartDate; }
        public void setWeekStartDate(String weekStartDate) { this.weekStartDate = weekStartDate; }

        public Double getAuraScore() { return auraScore; }
        public void setAuraScore(Double auraScore) { this.auraScore = auraScore; }

        public Double getTeamwork() { return teamwork; }
        public void setTeamwork(Double teamwork) { this.teamwork = teamwork; }

        public Double getInitiative() { return initiative; }
        public void setInitiative(Double initiative) { this.initiative = initiative; }

        public Double getAttitude() { return attitude; }
        public void setAttitude(Double attitude) { this.attitude = attitude; }
    }

    /**
     * Response for weekly ratings history
     */
    public static class WeeklyRatingsHistory {
        private String employeeId;
        private java.util.List<WeeklyTrendPoint> weeks;
        private Double averageAura;
        private Integer totalWeeksRated;

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public java.util.List<WeeklyTrendPoint> getWeeks() { return weeks; }
        public void setWeeks(java.util.List<WeeklyTrendPoint> weeks) { this.weeks = weeks; }

        public Double getAverageAura() { return averageAura; }
        public void setAverageAura(Double averageAura) { this.averageAura = averageAura; }

        public Integer getTotalWeeksRated() { return totalWeeksRated; }
        public void setTotalWeeksRated(Integer totalWeeksRated) { this.totalWeeksRated = totalWeeksRated; }
    }

    /**
     * DTO for each pillar's breakdown in the mobile app
     */
    public static class PillarBreakdown {
        private String pillarName;
        private String pillarKey;          // technical, behavioral, etc.
        private Double overallScore;       // 0-100
        private java.util.List<CriterionScore> criteria;

        public String getPillarName() { return pillarName; }
        public void setPillarName(String pillarName) { this.pillarName = pillarName; }

        public String getPillarKey() { return pillarKey; }
        public void setPillarKey(String pillarKey) { this.pillarKey = pillarKey; }

        public Double getOverallScore() { return overallScore; }
        public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }

        public java.util.List<CriterionScore> getCriteria() { return criteria; }
        public void setCriteria(java.util.List<CriterionScore> criteria) { this.criteria = criteria; }
    }

    /**
     * Individual criterion score within a pillar
     */
    public static class CriterionScore {
        private String name;
        private Double score;           // 0-100
        private Double weight;          // e.g., 5.0 (5%)
        private String source;          // "auto", "team_lead", "manager", "peer"
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }

        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
