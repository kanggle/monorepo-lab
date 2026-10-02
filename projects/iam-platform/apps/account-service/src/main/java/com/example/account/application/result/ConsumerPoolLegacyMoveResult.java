package com.example.account.application.result;

import java.util.List;
import java.util.Map;

/**
 * TASK-BE-618 — the report of one run of {@code POST /internal/consumer-pool/legacy-moves}
 * (account-maintenance-internal.md). Account ids only — never an email.
 *
 * @param scanned            candidates looked at in this run (≤ the limit)
 * @param moved              accounts moved
 * @param skipped            per skip reason ({@link LegacyMoveOutcome#isSkip()}), every reason present (0s included)
 * @param failed             accounts whose move rolled back for any other reason
 * @param movedAccountIds    moved account ids, at most {@link #ID_LIST_CAP}
 * @param failedAccountIds   failed account ids, at most {@link #ID_LIST_CAP}
 * @param nextAfterAccountId the cursor for the next run when this run filled its limit, else {@code null}
 */
public record ConsumerPoolLegacyMoveResult(
        int scanned,
        int moved,
        Map<String, Integer> skipped,
        int failed,
        List<String> movedAccountIds,
        List<String> failedAccountIds,
        String nextAfterAccountId) {

    public static final int ID_LIST_CAP = 100;
}
