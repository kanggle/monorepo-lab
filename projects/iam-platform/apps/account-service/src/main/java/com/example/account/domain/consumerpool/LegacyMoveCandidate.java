package com.example.account.domain.consumerpool;

import com.example.account.domain.tenant.TenantId;

/**
 * TASK-BE-618 — one candidate of the consumer-pool legacy move: a non-DELETED account living in a
 * consumer site. The candidate list is read without a lock; each candidate is re-read under a lock
 * ({@link LegacySiteAccount}) before anything is written.
 */
public record LegacyMoveCandidate(String accountId, TenantId siteTenantId) {
}
