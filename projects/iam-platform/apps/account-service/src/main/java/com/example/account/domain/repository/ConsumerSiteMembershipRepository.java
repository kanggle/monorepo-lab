package com.example.account.domain.repository;

import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.tenant.TenantId;

import java.util.Optional;

/**
 * TASK-BE-614 — port for {@code consumer_site_memberships} (ADR-MONO-078 A).
 *
 * <p>Follows the repository rule of multi-tenancy.md § 격리 회귀 방지: every lookup takes the
 * <b>site</b> tenant as its first argument.
 */
public interface ConsumerSiteMembershipRepository {

    /**
     * Inserts a new membership. The pool account row must already be written in the same
     * transaction (FK {@code account_id → accounts.id}); the implementation flushes pending
     * writes first so that ordering holds regardless of Hibernate's insert ordering.
     */
    void insert(ConsumerSiteMembership membership);

    /** The membership of {@code accountId} on {@code siteTenantId}, if any. */
    Optional<ConsumerSiteMembership> find(TenantId siteTenantId, String accountId);
}
