package com.schoolable.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BiometricConsentRepository extends JpaRepository<BiometricConsent, UUID> {
    Optional<BiometricConsent> findByUserId(UUID userId);
}
