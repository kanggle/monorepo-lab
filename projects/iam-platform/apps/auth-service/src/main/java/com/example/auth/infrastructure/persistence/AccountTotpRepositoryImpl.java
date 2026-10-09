package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.domain.repository.AccountTotpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * TASK-MONO-771 — {@link AccountTotpRepository} over JPA.
 *
 * <p>Update path: the managed row is loaded and the domain state copied onto it, after checking that the version
 * the caller read is still the stored one; Hibernate's {@code @Version} then guards the UPDATE itself. Two
 * concurrent submissions of the same code (or of the same recovery code) therefore cannot both win — the loser
 * gets {@link ObjectOptimisticLockingFailureException}, which the service reads as a failed attempt.
 */
@Repository
@RequiredArgsConstructor
public class AccountTotpRepositoryImpl implements AccountTotpRepository {

    private final AccountTotpJpaRepository jpaRepository;

    @Override
    public Optional<AccountTotp> findByAccountId(String accountId) {
        return jpaRepository.findById(accountId).map(AccountTotpJpaEntity::toDomain);
    }

    @Override
    public AccountTotp save(AccountTotp totp) {
        Optional<AccountTotpJpaEntity> existing = jpaRepository.findById(totp.getAccountId());
        if (existing.isEmpty()) {
            return jpaRepository.saveAndFlush(AccountTotpJpaEntity.fromDomain(totp)).toDomain();
        }
        AccountTotpJpaEntity managed = existing.get();
        if (!Objects.equals(managed.getVersion(), totp.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(AccountTotpJpaEntity.class, totp.getAccountId());
        }
        managed.apply(totp);
        return jpaRepository.saveAndFlush(managed).toDomain();
    }

    @Override
    public void deleteByAccountId(String accountId) {
        jpaRepository.findById(accountId).ifPresent(row -> {
            jpaRepository.delete(row);
            // Flush now: a pending row is replaced by delete-then-insert of the same key in one transaction,
            // and Hibernate would otherwise order the INSERT before the DELETE (duplicate key).
            jpaRepository.flush();
        });
    }
}
