package com.schoolable.backend.kpi;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/kpi/locks")
public class KpiLockController {

    private final KpiPeriodLockRepository lockRepository;

    public KpiLockController(KpiPeriodLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @PostMapping
    public ResponseEntity<?> createLock(@RequestBody LockRequest request, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        KpiPeriodLock lock = new KpiPeriodLock();
        lock.setKpiType(request.kpiType());
        lock.setQuarter(request.quarter());
        lock.setYear(request.year());
        lock.setDepartment(request.department());
        lock.setTeamLeadId(request.teamLeadId() != null ? UUID.fromString(request.teamLeadId()) : null);
        lock.setLockedBy((UUID) auth.getPrincipal());
        lock.setReason(request.reason());
        lock.setIsLocked(true);

        KpiPeriodLock saved = lockRepository.save(lock);
        return ResponseEntity.ok(Map.of(
            "id", saved.getId(),
            "kpiType", saved.getKpiType(),
            "quarter", saved.getQuarter(),
            "year", saved.getYear(),
            "department", saved.getDepartment(),
            "teamLeadId", saved.getTeamLeadId(),
            "lockedAt", saved.getLockedAt(),
            "reason", saved.getReason(),
            "isLocked", saved.getIsLocked()
        ));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<?> unlock(@PathVariable UUID id, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        KpiPeriodLock lock = lockRepository.findById(id).orElse(null);
        if (lock == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Lock not found"));
        }

        lock.setIsLocked(false);
        lockRepository.save(lock);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping
    public ResponseEntity<?> listLocks(Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        List<KpiPeriodLock> locks = lockRepository.findAll();
        return ResponseEntity.ok(locks);
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
            || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    public record LockRequest(
        String kpiType,
        String quarter,
        Integer year,
        String department,
        String teamLeadId,
        String reason
    ) {}
}
