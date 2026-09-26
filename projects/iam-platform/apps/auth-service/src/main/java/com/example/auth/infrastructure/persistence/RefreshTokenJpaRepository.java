package com.example.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByJti(String jti);

    boolean existsByRotatedFrom(String jti);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revoked = true WHERE r.accountId = :accountId AND r.revoked = false")
    int revokeAllByAccountId(@Param("accountId") String accountId);

    @Query("SELECT r.jti FROM RefreshTokenJpaEntity r WHERE r.accountId = :accountId AND r.revoked = false")
    List<String> findActiveJtisByAccountId(@Param("accountId") String accountId);

    /**
     * TASK-BE-606: every row rotated from the given token. {@code rotated_from} is not unique
     * (two concurrent refreshes of one token can each write a child), so this must not be an
     * {@code Optional} finder — that threw on the two-children state.
     */
    List<RefreshTokenJpaEntity> findAllByRotatedFrom(String rotatedFrom);

    @Query("SELECT r.jti FROM RefreshTokenJpaEntity r WHERE r.deviceId = :deviceId AND r.revoked = false")
    List<String> findActiveJtisByDeviceId(@Param("deviceId") String deviceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revoked = true WHERE r.deviceId = :deviceId AND r.revoked = false")
    int revokeAllByDeviceId(@Param("deviceId") String deviceId);
}
