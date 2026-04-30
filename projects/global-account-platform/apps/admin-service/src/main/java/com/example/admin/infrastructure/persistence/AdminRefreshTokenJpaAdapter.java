package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.AdminRefreshTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed adapter for {@link AdminRefreshTokenPort}. Delegates to
 * {@link AdminOperatorRefreshTokenJpaRepository}; introduced by
 * TASK-BE-040-fix to remove the application→infrastructure import path
 * previously present in {@code AdminRefreshTokenService},
 * {@code AdminLogoutService}, and {@code AdminRefreshTokenIssuer}.
 */
@Component
@RequiredArgsConstructor
public class AdminRefreshTokenJpaAdapter implements AdminRefreshTokenPort {

    private final AdminOperatorRefreshTokenJpaRepository repository;

    @Override
    public Optional<TokenRecord> findByJti(String jti) {
        return repository.findById(jti).map(AdminRefreshTokenJpaAdapter::toRecord);
    }

    @Override
    public void insert(NewTokenRecord row) {
        AdminOperatorRefreshTokenJpaEntity e = AdminOperatorRefreshTokenJpaEntity.issue(
                row.jti(), row.operatorInternalId(), row.issuedAt(), row.expiresAt(), row.rotatedFrom());
        repository.save(e);
    }

    @Override
    public void revoke(String jti, Instant at, String reason) {
        repository.findById(jti).ifPresent(entity -> {
            if (!entity.isRevoked()) {
                entity.revoke(at, reason);
                repository.save(entity);
            }
        });
    }

    @Override
    public int revokeAllForOperator(Long operatorInternalId, Instant at, String reason) {
        return repository.revokeAllForOperator(operatorInternalId, at, reason);
    }

    private static TokenRecord toRecord(AdminOperatorRefreshTokenJpaEntity e) {
        return new TokenRecord(
                e.getJti(),
                e.getOperatorId(),
                e.getIssuedAt(),
                e.getExpiresAt(),
                e.getRotatedFrom(),
                e.getRevokedAt(),
                e.getRevokeReason());
    }
}
