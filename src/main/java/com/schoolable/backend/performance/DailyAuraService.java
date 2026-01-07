package com.schoolable.backend.performance;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Daily Aura Calculation Service
 * Runs daily at 11:59 PM to calculate and store daily Aura scores.
 * Also handles trend detection and alert generation.
 */
@Service
public class DailyAuraService {

    @Autowired
    private DailyAuraSnapshotRepository snapshotRepository;

    @Autowired
    private AuraTrendAlertRepository alertRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AutoAuraCalculationService auraCalculationService;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    // ==================== SCHEDULED DAILY CALCULATION ====================

    /**
     * Run daily at 11:59 PM to calculate and store daily Aura
     */
    @Scheduled(cron = "0 59 23 * * *")
    @Transactional
    public void calculateDailyAura() {
        System.out.println("Starting daily Aura calculation at " + LocalDate.now());

        List<Profile> activeEmployees = profileRepository.findByStatusAndProfileCompletedAtIsNotNull("active");

        int processed = 0;
        int errors = 0;

        for (Profile employee : activeEmployees) {
            try {
                calculateAndStoreForEmployee(employee);
                processed++;
            } catch (Exception e) {
                System.err.println("Error calculating daily Aura for " + employee.getId() + ": " + e.getMessage());
                errors++;
            }
        }

        System.out.println("Daily Aura calculation complete: " + processed + " processed, " + errors + " errors");

        // After all calculations, check for trends
        checkWeeklyTrends();
    }

    // ==================== CALCULATE FOR SINGLE EMPLOYEE ====================

    public DailyAuraSnapshot calculateAndStoreForEmployee(Profile employee) {
        LocalDate today = LocalDate.now();
        UUID employeeId = employee.getId();

        // Check if already calculated today
        Optional<DailyAuraSnapshot> existing = snapshotRepository.findByEmployeeIdAndSnapshotDate(
            employeeId, today);
        
        if (existing.isPresent()) {
            return existing.get(); // Already done
        }

        // Create snapshot
        DailyAuraSnapshot snapshot = new DailyAuraSnapshot(employeeId, today);

        // Check daily activity
        boolean submittedReport = dailyReportRepository.existsByEmployeeIdAndReportDate(employeeId, today);
        snapshot.setDailyReportSubmitted(submittedReport);

        // Get latest Aura calculation from main service
        // Use a lightweight version for daily updates
        try {
            BigDecimal[] pillarScores = calculateLightweightAura(employee);
            
            snapshot.setTechnicalScore(pillarScores[0]);
            snapshot.setBehavioralScore(pillarScores[1]);
            snapshot.setCultureFitScore(pillarScores[2]);
            snapshot.setGrowthScore(pillarScores[3]);
            
            // Calculate weighted total
            BigDecimal dailyAura = pillarScores[0].multiply(BigDecimal.valueOf(0.35))
                .add(pillarScores[1].multiply(BigDecimal.valueOf(0.25)))
                .add(pillarScores[2].multiply(BigDecimal.valueOf(0.20)))
                .add(pillarScores[3].multiply(BigDecimal.valueOf(0.20)));
            
            snapshot.setDailyAura(dailyAura.setScale(2, RoundingMode.HALF_UP));

            // Calculate change from previous day
            Optional<DailyAuraSnapshot> previous = snapshotRepository.findPreviousSnapshot(employeeId, today);
            if (previous.isPresent() && previous.get().getDailyAura() != null) {
                BigDecimal change = dailyAura.subtract(previous.get().getDailyAura());
                snapshot.setAuraChange(change.setScale(2, RoundingMode.HALF_UP));
            }

        } catch (Exception e) {
            System.err.println("Error in lightweight Aura calc: " + e.getMessage());
            // Default to 75 if calculation fails
            snapshot.setDailyAura(BigDecimal.valueOf(75));
        }

        snapshotRepository.save(snapshot);

        // Check for significant changes and create alerts
        checkForAlerts(employeeId, snapshot);

        return snapshot;
    }

    // ==================== LIGHTWEIGHT AURA CALCULATION ====================

    /**
     * Simplified Aura calculation for daily updates
     * Uses cached/recent data rather than full complex calculation
     */
    private BigDecimal[] calculateLightweightAura(Profile employee) {
        BigDecimal technical = BigDecimal.valueOf(75);
        BigDecimal behavioral = BigDecimal.valueOf(75);
        BigDecimal cultureFit = BigDecimal.valueOf(75);
        BigDecimal growth = BigDecimal.valueOf(75);

        // Get recent daily report scores
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        Double avgReportScore = dailyReportRepository.getAverageAiScore(
            employee.getId(), thirtyDaysAgo, LocalDate.now());
        if (avgReportScore != null) {
            technical = BigDecimal.valueOf(avgReportScore);
        }

        // Use last weekly Aura calculation for other pillars if available
        Optional<DailyAuraSnapshot> lastSnapshot = snapshotRepository.findLatestByEmployeeId(employee.getId());
        if (lastSnapshot.isPresent()) {
            DailyAuraSnapshot last = lastSnapshot.get();
            if (last.getBehavioralScore() != null) behavioral = last.getBehavioralScore();
            if (last.getCultureFitScore() != null) cultureFit = last.getCultureFitScore();
            if (last.getGrowthScore() != null) growth = last.getGrowthScore();
        }

        return new BigDecimal[] { technical, behavioral, cultureFit, growth };
    }

    // ==================== ALERT DETECTION ====================

    private void checkForAlerts(UUID employeeId, DailyAuraSnapshot current) {
        // Only check weekly (compare to 7 days ago)
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        Optional<DailyAuraSnapshot> weekAgoSnapshot = snapshotRepository.findByEmployeeAndDate(
            employeeId, weekAgo);

        if (weekAgoSnapshot.isEmpty()) return;

        BigDecimal previousAura = weekAgoSnapshot.get().getDailyAura();
        BigDecimal currentAura = current.getDailyAura();

        if (previousAura == null || currentAura == null) return;
        if (previousAura.compareTo(BigDecimal.ZERO) == 0) return;

        // Calculate percentage change
        BigDecimal changePercent = currentAura.subtract(previousAura)
            .multiply(BigDecimal.valueOf(100))
            .divide(previousAura, 2, RoundingMode.HALF_UP);

        // Check for significant drop (> 10%)
        if (changePercent.compareTo(BigDecimal.valueOf(-10)) < 0) {
            createAlert(employeeId, AuraTrendAlert.TYPE_SCORE_DROP, 
                previousAura, currentAura, 
                "Aura score dropped by " + changePercent.abs() + "% this week");
        }
        // Check for significant improvement (> 10%)
        else if (changePercent.compareTo(BigDecimal.valueOf(10)) > 0) {
            createAlert(employeeId, AuraTrendAlert.TYPE_SCORE_INCREASE,
                previousAura, currentAura,
                "Aura score improved by " + changePercent + "% this week");
        }
    }

    private void checkWeeklyTrends() {
        // This runs after daily calculations
        // Check for consistent trends (3+ weeks of decline/improvement)
        
        List<Profile> employees = profileRepository.findByStatusAndProfileCompletedAtIsNotNull("active");
        
        for (Profile employee : employees) {
            checkConsistentTrend(employee.getId());
        }
    }

    private void checkConsistentTrend(UUID employeeId) {
        LocalDate today = LocalDate.now();
        List<DailyAuraSnapshot> recentSnapshots = snapshotRepository
            .findByEmployeeIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                employeeId, today.minusDays(21), today);

        if (recentSnapshots.size() < 14) return; // Need at least 2 weeks of data

        // Check week-over-week trend
        int decliningWeeks = 0;
        int improvingWeeks = 0;

        for (int i = 7; i < recentSnapshots.size(); i += 7) {
            BigDecimal thisWeek = recentSnapshots.get(i).getDailyAura();
            BigDecimal lastWeek = recentSnapshots.get(i - 7).getDailyAura();

            if (thisWeek == null || lastWeek == null) continue;

            if (thisWeek.compareTo(lastWeek) < 0) {
                decliningWeeks++;
                improvingWeeks = 0;
            } else if (thisWeek.compareTo(lastWeek) > 0) {
                improvingWeeks++;
                decliningWeeks = 0;
            }
        }

        if (decliningWeeks >= 3) {
            BigDecimal first = recentSnapshots.get(0).getDailyAura();
            BigDecimal last = recentSnapshots.get(recentSnapshots.size() - 1).getDailyAura();
            
            AuraTrendAlert alert = new AuraTrendAlert(employeeId, AuraTrendAlert.TYPE_CONSISTENT_DECLINE,
                first, last, "Performance has been declining for " + decliningWeeks + " consecutive weeks");
            alert.setWeeksTrending(decliningWeeks);
            alertRepository.save(alert);
        }

        if (improvingWeeks >= 3) {
            BigDecimal first = recentSnapshots.get(0).getDailyAura();
            BigDecimal last = recentSnapshots.get(recentSnapshots.size() - 1).getDailyAura();
            
            AuraTrendAlert alert = new AuraTrendAlert(employeeId, AuraTrendAlert.TYPE_CONSISTENT_IMPROVEMENT,
                first, last, "Performance has been improving for " + improvingWeeks + " consecutive weeks");
            alert.setWeeksTrending(improvingWeeks);
            alertRepository.save(alert);
        }
    }

    private void createAlert(UUID employeeId, String type, BigDecimal previous, BigDecimal current, String message) {
        AuraTrendAlert alert = new AuraTrendAlert(employeeId, type, previous, current, message);
        alertRepository.save(alert);
    }

    // ==================== PUBLIC API ====================

    /**
     * Get daily Aura trend for an employee (last N days)
     */
    public List<DailyAuraSnapshot> getAuraTrend(UUID employeeId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return snapshotRepository.findByEmployeeIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            employeeId, startDate, LocalDate.now());
    }

    /**
     * Get today's snapshot for an employee
     */
    public Optional<DailyAuraSnapshot> getTodaySnapshot(UUID employeeId) {
        return snapshotRepository.findByEmployeeIdAndSnapshotDate(employeeId, LocalDate.now());
    }

    /**
     * Force recalculation for an employee (e.g., after daily report submission)
     */
    public DailyAuraSnapshot forceRecalculate(UUID employeeId) {
        Profile employee = profileRepository.findById(employeeId).orElse(null);
        if (employee == null) return null;
        
        // Delete existing snapshot for today
        Optional<DailyAuraSnapshot> existing = snapshotRepository.findByEmployeeIdAndSnapshotDate(
            employeeId, LocalDate.now());
        existing.ifPresent(snapshotRepository::delete);
        
        return calculateAndStoreForEmployee(employee);
    }
}
