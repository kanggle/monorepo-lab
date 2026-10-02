package com.example.account.presentation.dto.response;

import com.example.account.application.result.ConsumerPoolLegacyMoveResult;

import java.util.List;
import java.util.Map;

/**
 * TASK-BE-618 — 200 body of {@code POST /internal/consumer-pool/legacy-moves}
 * (account-maintenance-internal.md). Account ids only — never an email.
 */
public record ConsumerPoolLegacyMoveResponse(
        int scanned,
        int moved,
        Map<String, Integer> skipped,
        int failed,
        List<String> movedAccountIds,
        List<String> failedAccountIds,
        String nextAfterAccountId) {

    public static ConsumerPoolLegacyMoveResponse from(ConsumerPoolLegacyMoveResult r) {
        return new ConsumerPoolLegacyMoveResponse(r.scanned(), r.moved(), r.skipped(), r.failed(),
                r.movedAccountIds(), r.failedAccountIds(), r.nextAfterAccountId());
    }
}
