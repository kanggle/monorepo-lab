package com.example.auth.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceSessionJpaRepository extends JpaRepository<DeviceSessionJpaEntity, Long> {

    Optional<DeviceSessionJpaEntity> findByDeviceId(String deviceId);

    @Query("SELECT d FROM DeviceSessionJpaEntity d " +
            "WHERE d.accountId = :accountId AND d.deviceFingerprint = :fp AND d.revokedAt IS NULL")
    Optional<DeviceSessionJpaEntity> findActiveByAccountAndFingerprint(
            @Param("accountId") String accountId, @Param("fp") String fingerprint);

    @Query("SELECT d FROM DeviceSessionJpaEntity d " +
            "WHERE d.accountId = :accountId AND d.revokedAt IS NULL " +
            "ORDER BY d.lastSeenAt DESC")
    List<DeviceSessionJpaEntity> findActiveByAccountIdOrderByLastSeenDesc(
            @Param("accountId") String accountId);

    @Query("SELECT COUNT(d) FROM DeviceSessionJpaEntity d " +
            "WHERE d.accountId = :accountId AND d.revokedAt IS NULL")
    long countActiveByAccountId(@Param("accountId") String accountId);

    @Query("SELECT d FROM DeviceSessionJpaEntity d " +
            "WHERE d.accountId = :accountId AND d.revokedAt IS NULL " +
            "ORDER BY d.lastSeenAt ASC")
    List<DeviceSessionJpaEntity> findOldestActiveByAccountId(
            @Param("accountId") String accountId, Pageable pageable);
}
