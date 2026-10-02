package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-618 — optional body of {@code POST /internal/consumer-pool/legacy-moves}
 * (account-maintenance-internal.md).
 *
 * @param limit          candidates to look at in this run; {@code null} → 500; 1..1000
 * @param afterAccountId cursor — look only at account ids greater than this; {@code null} → from the start
 */
public record ConsumerPoolLegacyMoveRequest(
        @Min(1) @Max(1000) Integer limit,
        @Size(max = 36) String afterAccountId
) {
}
