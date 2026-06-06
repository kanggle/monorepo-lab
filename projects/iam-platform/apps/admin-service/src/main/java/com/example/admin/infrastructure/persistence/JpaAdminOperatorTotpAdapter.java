package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.AdminOperatorTotpPort;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-BE-288 — JPA-backed adapter for {@link AdminOperatorTotpPort}. Wraps
 * {@code AdminOperatorTotpJpaRepository} and translates the JPA-specific
 * optimistic-lock exception types ({@link OptimisticLockException},
 * {@link ObjectOptimisticLockingFailureException}) into a boolean return
 * ({@link #tryUpdateRecoveryHashes}) so the application layer can drive its
 * retry loop without importing JPA.
 */
@Component
@RequiredArgsConstructor
public class JpaAdminOperatorTotpAdapter implements AdminOperatorTotpPort {

    private final AdminOperatorTotpJpaRepository totpRepository;

    @Override
    public Optional<TotpRow> findByOperator(long operatorInternalId) {
        return totpRepository.findById(operatorInternalId).map(JpaAdminOperatorTotpAdapter::toRow);
    }

    @Override
    public Set<Long> findEnrolledOperatorIds(Collection<Long> operatorInternalIds) {
        if (operatorInternalIds == null || operatorInternalIds.isEmpty()) return Set.of();
        Set<Long> enrolled = new HashSet<>();
        for (AdminOperatorTotpJpaEntity row : totpRepository.findByOperatorIdIn(operatorInternalIds)) {
            if (row != null && row.getEnrolledAt() != null) enrolled.add(row.getOperatorId());
        }
        return enrolled;
    }

    @Override
    public void upsertSecret(long operatorInternalId,
                             byte[] secretEncrypted,
                             String secretKeyId,
                             String recoveryCodesHashed,
                             Instant enrolledAt) {
        AdminOperatorTotpJpaEntity existing = totpRepository.findById(operatorInternalId).orElse(null);
        if (existing == null) {
            totpRepository.save(AdminOperatorTotpJpaEntity.create(
                    operatorInternalId, secretEncrypted, secretKeyId, recoveryCodesHashed, enrolledAt));
        } else {
            existing.replaceSecret(secretEncrypted, secretKeyId, recoveryCodesHashed, enrolledAt);
            totpRepository.save(existing);
        }
    }

    @Override
    public void markUsed(long operatorInternalId, Instant at) {
        totpRepository.findById(operatorInternalId).ifPresent(row -> {
            row.markUsed(at);
            totpRepository.save(row);
        });
    }

    @Override
    public void replaceRecoveryHashes(long operatorInternalId,
                                     String recoveryCodesHashed,
                                     Instant at) {
        totpRepository.findById(operatorInternalId).ifPresent(row -> {
            row.replaceRecoveryHashes(recoveryCodesHashed, at);
            totpRepository.save(row);
        });
    }

    @Override
    public boolean tryUpdateRecoveryHashes(long operatorInternalId,
                                          int expectedVersion,
                                          String recoveryCodesHashed,
                                          Instant at) {
        AdminOperatorTotpJpaEntity row = totpRepository.findById(operatorInternalId).orElse(null);
        if (row == null) return false;
        if (row.getVersion() != expectedVersion) {
            return false;
        }
        row.replaceRecoveryHashes(recoveryCodesHashed, at);
        try {
            totpRepository.saveAndFlush(row);
            return true;
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            return false;
        }
    }

    private static TotpRow toRow(AdminOperatorTotpJpaEntity e) {
        return new TotpRow(
                e.getOperatorId(),
                e.getSecretEncrypted(),
                e.getSecretKeyId(),
                e.getRecoveryCodesHashed(),
                e.getLastUsedAt(),
                e.getEnrolledAt(),
                e.getVersion());
    }
}
