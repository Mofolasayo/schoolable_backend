package com.schoolable.backend.performance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.schoolable.backend.storage.StorageService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * REST controller for training records/certificates.
 * Handles certificate uploads and admin approval workflow.
 */
@RestController
@RequestMapping("/api/performance/training-records")
public class TrainingRecordController {

    @Autowired
    private TrainingRecordRepository trainingRecordRepository;

    @Autowired
    private StorageService storageService;

    /**
     * Upload a new certificate (employee action)
     */
    @PostMapping
    public ResponseEntity<?> uploadCertificate(
            Authentication auth,
            @RequestParam("certificate") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "quarter", required = false) String quarter,
            @RequestParam(value = "year", required = false) Integer year) {
        
        try {
            UUID employeeId = UUID.fromString(auth.getName());
            
            // Default to current quarter/year if not provided
            if (quarter == null) {
                int month = LocalDate.now().getMonthValue();
                if (month <= 3) quarter = "Q1";
                else if (month <= 6) quarter = "Q2";
                else if (month <= 9) quarter = "Q3";
                else quarter = "Q4";
            }
            if (year == null) {
                year = LocalDate.now().getYear();
            }

            // Check if already submitted for this quarter
            Optional<TrainingRecord> existing = trainingRecordRepository
                .findByEmployeeIdAndQuarterAndYear(employeeId, quarter, year);
            
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "You have already submitted a certificate for " + quarter + " " + year
                ));
            }

            // Upload file to storage
            Map<String, Object> uploadResult = storageService.uploadFile(file, "certificates/" + employeeId);
            String fileUrl = (String) uploadResult.get("url");

            // Create training record
            TrainingRecord record = new TrainingRecord();
            record.setEmployeeId(employeeId);
            record.setTrainingName(name);
            record.setTrainingType("certification");
            record.setCertificateUrl(fileUrl);
            record.setQuarter(quarter);
            record.setYear(year);
            record.setStatus("pending");
            record.setCompletionDate(LocalDate.now());

            trainingRecordRepository.save(record);

            return ResponseEntity.ok(Map.of(
                "message", "Certificate uploaded successfully. Pending admin approval.",
                "id", record.getId(),
                "status", "pending"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to upload certificate: " + e.getMessage()
            ));
        }
    }

    /**
     * Get my certificates (employee view)
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyCertificates(Authentication auth) {
        try {
            UUID employeeId = UUID.fromString(auth.getName());
            List<TrainingRecord> records = trainingRecordRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (TrainingRecord r : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.getId());
                item.put("name", r.getTrainingName());
                item.put("quarter", r.getQuarter());
                item.put("year", r.getYear());
                item.put("status", r.getStatus());
                item.put("certificateUrl", r.getCertificateUrl());
                item.put("createdAt", r.getCreatedAt());
                item.put("approvedAt", r.getApprovedAt());
                item.put("rejectionReason", r.getRejectionReason());
                result.add(item);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to fetch certificates: " + e.getMessage()
            ));
        }
    }

    /**
     * Check if I have a certificate for current quarter
     */
    @GetMapping("/my/current-quarter")
    public ResponseEntity<?> getCurrentQuarterStatus(Authentication auth) {
        try {
            UUID employeeId = UUID.fromString(auth.getName());
            
            int month = LocalDate.now().getMonthValue();
            String quarter;
            if (month <= 3) quarter = "Q1";
            else if (month <= 6) quarter = "Q2";
            else if (month <= 9) quarter = "Q3";
            else quarter = "Q4";
            int year = LocalDate.now().getYear();

            Optional<TrainingRecord> record = trainingRecordRepository
                .findByEmployeeIdAndQuarterAndYear(employeeId, quarter, year);

            if (record.isPresent()) {
                TrainingRecord r = record.get();
                return ResponseEntity.ok(Map.of(
                    "hasSubmitted", true,
                    "status", r.getStatus(),
                    "quarter", quarter,
                    "year", year,
                    "certificateName", r.getTrainingName(),
                    "isApproved", "approved".equals(r.getStatus())
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "hasSubmitted", false,
                    "quarter", quarter,
                    "year", year
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get all pending certificates (admin view)
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingCertificates() {
        try {
            List<TrainingRecord> pending = trainingRecordRepository
                .findByStatusOrderByCreatedAtAsc("pending");
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (TrainingRecord r : pending) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.getId());
                item.put("employeeId", r.getEmployeeId());
                item.put("name", r.getTrainingName());
                item.put("quarter", r.getQuarter());
                item.put("year", r.getYear());
                item.put("status", r.getStatus());
                item.put("certificateUrl", r.getCertificateUrl());
                item.put("createdAt", r.getCreatedAt());
                result.add(item);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get all certificates (admin view)
     */
    @GetMapping
    public ResponseEntity<?> getAllCertificates(
            @RequestParam(value = "quarter", required = false) String quarter,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "status", required = false) String status) {
        try {
            List<TrainingRecord> records;
            
            if (quarter != null && year != null) {
                records = trainingRecordRepository
                    .findByQuarterAndYearOrderByCreatedAtDesc(quarter, year);
            } else if (status != null) {
                records = trainingRecordRepository.findByStatusOrderByCreatedAtAsc(status);
            } else {
                records = trainingRecordRepository.findAll();
            }
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (TrainingRecord r : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.getId());
                item.put("employeeId", r.getEmployeeId());
                item.put("name", r.getTrainingName());
                item.put("type", r.getTrainingType());
                item.put("quarter", r.getQuarter());
                item.put("year", r.getYear());
                item.put("status", r.getStatus());
                item.put("certificateUrl", r.getCertificateUrl());
                item.put("createdAt", r.getCreatedAt());
                item.put("approvedBy", r.getApprovedBy());
                item.put("approvedAt", r.getApprovedAt());
                item.put("rejectionReason", r.getRejectionReason());
                result.add(item);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get certificates for a specific employee (admin/HR view)
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeCertificates(@PathVariable UUID employeeId) {
        try {
            List<TrainingRecord> records = trainingRecordRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (TrainingRecord r : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.getId());
                item.put("name", r.getTrainingName());
                item.put("type", r.getTrainingType());
                item.put("quarter", r.getQuarter());
                item.put("year", r.getYear());
                item.put("status", r.getStatus());
                item.put("certificateUrl", r.getCertificateUrl());
                item.put("completionDate", r.getCompletionDate());
                item.put("createdAt", r.getCreatedAt());
                item.put("approvedAt", r.getApprovedAt());
                result.add(item);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Approve a certificate (admin action)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCertificate(
            @PathVariable Long id,
            Authentication auth) {
        try {
            UUID adminId = UUID.fromString(auth.getName());
            
            Optional<TrainingRecord> optRecord = trainingRecordRepository.findById(id);
            if (optRecord.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            TrainingRecord record = optRecord.get();
            record.setStatus("approved");
            record.setApprovedBy(adminId);
            record.setApprovedAt(OffsetDateTime.now());
            
            trainingRecordRepository.save(record);

            return ResponseEntity.ok(Map.of(
                "message", "Certificate approved successfully",
                "id", id
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Reject a certificate (admin action)
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectCertificate(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        try {
            Optional<TrainingRecord> optRecord = trainingRecordRepository.findById(id);
            if (optRecord.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            TrainingRecord record = optRecord.get();
            record.setStatus("rejected");
            record.setRejectionReason(body.get("reason"));
            
            trainingRecordRepository.save(record);

            return ResponseEntity.ok(Map.of(
                "message", "Certificate rejected",
                "id", id
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}
