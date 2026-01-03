package com.schoolable.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<DeviceToken> findByUserIdAndToken(UUID userId, String token);

    @Query("SELECT DISTINCT d.token FROM DeviceToken d WHERE d.userId = :userId AND d.isActive = true")
    List<String> findActiveTokensByUserId(@Param("userId") UUID userId);

    @Query("SELECT d FROM DeviceToken d WHERE d.userId IN :userIds AND d.isActive = true")
    List<DeviceToken> findActiveByUserIds(@Param("userIds") List<UUID> userIds);

    @Modifying
    @Query("UPDATE DeviceToken d SET d.isActive = false WHERE d.token = :token")
    void deactivateToken(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndToken(UUID userId, String token);
}
