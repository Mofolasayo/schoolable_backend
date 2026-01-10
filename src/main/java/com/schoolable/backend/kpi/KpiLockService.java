package com.schoolable.backend.kpi;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class KpiLockService {

    private final KpiPeriodLockRepository lockRepository;

    public KpiLockService(KpiPeriodLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    public boolean isLocked(String kpiType, String quarter, Integer year, UUID teamLeadId, String department) {
        return lockRepository.findActiveLock(kpiType, quarter, year, teamLeadId, department).isPresent();
    }
}
