package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccountRoleRepositoryAdapter implements AccountRoleRepository {

    /**
     * MySQL vendor error code for {@code ER_DUP_ENTRY} (duplicate key for a
     * UNIQUE / PRIMARY KEY index). See MySQL Server Error Reference.
     */
    private static final int MYSQL_ER_DUP_ENTRY = 1062;

    private final AccountRoleJpaRepository jpaRepository;

    @Override
    public AccountRole save(AccountRole role) {
        AccountRoleJpaEntity entity = AccountRoleJpaEntity.fromDomain(role);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public List<AccountRole> findByTenantIdAndAccountId(TenantId tenantId, String accountId) {
        return jpaRepository.findByTenantIdAndAccountId(tenantId.value(), accountId)
                .stream()
                .map(AccountRoleJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllByTenantIdAndAccountId(TenantId tenantId, String accountId) {
        jpaRepository.deleteByTenantIdAndAccountId(tenantId.value(), accountId);
    }

    /**
     * TASK-BE-255: Insert the role only when the (tenant, account, role) triple
     * is not already present. Returns {@code true} when a new row was written.
     *
     * <p>TASK-BE-265: The original {@code findBy + save} pair leaves a TOCTOU
     * window — two concurrent requests for the same (tenant, account, role)
     * pass the {@code findBy} check and race on {@code save}. The loser hits
     * the composite PK and the JPA layer raises
     * {@link DataIntegrityViolationException}, which without explicit handling
     * surfaces as 500 and breaks the idempotent 200 contract documented in
     * {@code account-internal-provisioning.md}.
     *
     * <p>We catch the exception and narrow it to the duplicate-key case
     * (MySQL error 1062 / {@code ER_DUP_ENTRY}); other integrity violations
     * (notably FK 1452 from a non-existent account row) are re-thrown so the
     * caller and outer advice still see them.
     */
    @Override
    @Transactional
    public boolean addIfAbsent(AccountRole role) {
        var existing = jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                role.getTenantId().value(), role.getAccountId(), role.getRoleName());
        if (existing.isPresent()) {
            return false;
        }
        try {
            jpaRepository.save(AccountRoleJpaEntity.fromDomain(role));
            return true;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                // Concurrent insert won the race: the row already exists, so
                // this call is a no-op (idempotent). Mirror the early-exit
                // branch above by returning false.
                return false;
            }
            throw e;
        }
    }

    /**
     * TASK-BE-255: Remove the role only when present. Returns {@code true} when
     * a row was deleted.
     */
    @Override
    @Transactional
    public boolean removeIfPresent(TenantId tenantId, String accountId, String roleName) {
        int deleted = jpaRepository.deleteByTenantIdAndAccountIdAndRoleName(
                tenantId.value(), accountId, roleName);
        return deleted > 0;
    }

    /**
     * Walk the cause chain looking for a {@link SQLException} whose vendor
     * error code is MySQL's {@code ER_DUP_ENTRY} (1062). Hibernate wraps the
     * driver exception once (its own {@code ConstraintViolationException})
     * and Spring wraps that again ({@link DataIntegrityViolationException}),
     * so a single {@code getCause()} is not sufficient.
     */
    private static boolean isDuplicateKey(DataIntegrityViolationException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException sql && sql.getErrorCode() == MYSQL_ER_DUP_ENTRY) {
                return true;
            }
            Throwable next = t.getCause();
            if (next == t) {
                return false;
            }
            t = next;
        }
        return false;
    }
}
