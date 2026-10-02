package com.example.account.infrastructure.persistence;

import com.example.account.domain.consumerpool.LegacyMoveCandidate;
import com.example.account.domain.consumerpool.LegacySiteAccount;
import com.example.account.domain.repository.ConsumerPoolLegacyMoveRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-618 — JDBC adapter for {@link ConsumerPoolLegacyMoveRepository}.
 *
 * <p>Why plain SQL rather than the JPA entities: the move rewrites {@code tenant_id} on rows of four tables
 * and copies rows between two tables, all keyed on columns some entities leave unmapped
 * ({@code accounts.identity_id}) — loading entities only to rewrite one column would also put stale managed
 * copies in the persistence context. {@link JdbcTemplate} runs on the connection the surrounding JPA
 * transaction holds (JpaTransactionManager exposes it), so every statement here commits or rolls back with
 * the caller's per-account transaction.
 *
 * <p>{@code accounts.version} and {@code identities.version} are bumped: a concurrent load-modify-save that
 * still holds the site tenant then fails its optimistic lock instead of writing the site back.
 */
@Repository
@RequiredArgsConstructor
public class ConsumerPoolLegacyMoveRepositoryImpl implements ConsumerPoolLegacyMoveRepository {

    private static final String POOL = TenantId.CONSUMER_POOL.value();

    private final JdbcTemplate jdbc;

    @Override
    public List<LegacyMoveCandidate> findCandidates(List<TenantId> consumerSites, String afterAccountId, int limit) {
        if (consumerSites.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, tenant_id FROM accounts WHERE tenant_id IN (");
        sql.append(String.join(", ", Collections.nCopies(consumerSites.size(), "?"))).append(')');
        consumerSites.forEach(site -> args.add(site.value()));
        sql.append(" AND status <> 'DELETED'");
        if (afterAccountId != null) {
            sql.append(" AND id > ?");
            args.add(afterAccountId);
        }
        sql.append(" ORDER BY id LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(),
                (rs, i) -> new LegacyMoveCandidate(rs.getString("id"), new TenantId(rs.getString("tenant_id"))),
                args.toArray());
    }

    @Override
    public Optional<LegacySiteAccount> lockSiteAccount(TenantId siteTenantId, String accountId) {
        List<LegacySiteAccount> rows = jdbc.query(
                "SELECT id, tenant_id, email, status, identity_id, created_at FROM accounts "
                        + "WHERE tenant_id = ? AND id = ? AND status <> 'DELETED' FOR UPDATE",
                (rs, i) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new LegacySiteAccount(
                            rs.getString("id"),
                            new TenantId(rs.getString("tenant_id")),
                            rs.getString("email"),
                            AccountStatus.valueOf(rs.getString("status")),
                            rs.getString("identity_id"),
                            createdAt.toInstant());
                },
                siteTenantId.value(), accountId);
        return rows.stream().findFirst();
    }

    @Override
    public List<String> findSiteRoleNames(TenantId siteTenantId, String accountId) {
        return jdbc.queryForList(
                "SELECT role_name FROM account_roles WHERE tenant_id = ? AND account_id = ? ORDER BY role_name",
                String.class, siteTenantId.value(), accountId);
    }

    @Override
    public boolean identityWouldConflict(TenantId siteTenantId, String identityId, String accountId) {
        if (identityId == null) {
            return false;
        }
        Integer poolTwins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM identities pool_identity "
                        + "JOIN identities own ON own.identity_id = ? "
                        + "WHERE pool_identity.tenant_id = ? AND pool_identity.primary_email = own.primary_email "
                        + "AND pool_identity.identity_id <> own.identity_id",
                Integer.class, identityId, POOL);
        if (poolTwins != null && poolTwins > 0) {
            return true;
        }
        Integer otherAccounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE identity_id = ? AND id <> ?",
                Integer.class, identityId, accountId);
        return otherAccounts != null && otherAccounts > 0;
    }

    @Override
    public void insertActiveMembershipConsentedAtCreation(TenantId siteTenantId, String accountId) {
        int inserted = jdbc.update(
                "INSERT INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at) "
                        + "SELECT id, ?, 'ACTIVE', created_at FROM accounts WHERE id = ? AND tenant_id = ?",
                siteTenantId.value(), accountId, siteTenantId.value());
        if (inserted != 1) {
            throw new IllegalStateException("membership not inserted for account " + accountId);
        }
    }

    @Override
    public int copySiteRolesToConsumerSiteRoles(TenantId siteTenantId, String accountId) {
        return jdbc.update(
                "INSERT INTO consumer_site_roles (account_id, site_tenant_id, role_name, granted_by, granted_at) "
                        + "SELECT account_id, tenant_id, role_name, granted_by, granted_at FROM account_roles "
                        + "WHERE tenant_id = ? AND account_id = ?",
                siteTenantId.value(), accountId);
    }

    @Override
    public int deleteSiteRoles(TenantId siteTenantId, String accountId) {
        return jdbc.update("DELETE FROM account_roles WHERE tenant_id = ? AND account_id = ?",
                siteTenantId.value(), accountId);
    }

    @Override
    public int moveAccountRowsToPool(TenantId siteTenantId, String accountId, String identityId) {
        int accounts = jdbc.update(
                "UPDATE accounts SET tenant_id = ?, version = version + 1 WHERE id = ? AND tenant_id = ?",
                POOL, accountId, siteTenantId.value());
        if (accounts != 1) {
            return accounts;
        }
        jdbc.update("UPDATE profiles SET tenant_id = ? WHERE account_id = ?", POOL, accountId);
        if (identityId != null) {
            jdbc.update("UPDATE identities SET tenant_id = ?, version = version + 1 WHERE identity_id = ?",
                    POOL, identityId);
        }
        return accounts;
    }
}
