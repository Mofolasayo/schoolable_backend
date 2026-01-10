package com.schoolable.backend.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AttendanceRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceRetentionService.class);

    private final AttendanceRepository attendanceRepository;

    public AttendanceRetentionService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void redactExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Attendance> expired = attendanceRepository.findByRetentionUntilBefore(now);
        if (expired.isEmpty()) {
            return;
        }

        for (Attendance attendance : expired) {
            attendance.setPhotoUrl(null);
            attendance.setFaceMatchScore(null);
            attendance.setLivenessScore(null);
            attendance.setLivenessType(null);
            attendance.setLivenessPassed(null);
            attendance.setFaceMatchProvider(null);
            attendance.setLatitude(null);
            attendance.setLongitude(null);
            attendance.setAccuracy(null);
            attendance.setAddress(null);
            attendance.setDistanceMeters(null);
            attendance.setIsWithinGeofence(null);
            attendance.setDeviceInfo(null);
            attendance.setIpAddress(null);
            attendance.setRetentionUntil(null);
        }

        attendanceRepository.saveAll(expired);
        log.info("Redacted attendance biometric/location data for {} records", expired.size());
    }
}
