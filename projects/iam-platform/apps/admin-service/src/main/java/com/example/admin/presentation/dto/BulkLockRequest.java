package com.example.admin.presentation.dto;

import com.example.admin.presentation.ClientIdempotencyKey;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk-lock request payload.
 *
 * <p>The batch cap (≤100 accountIds) is NOT enforced at the bean-validation
 * layer — it is enforced in {@code BulkLockAccountUseCase#validate} so that
 * exceeding the cap surfaces as 422 {@code BATCH_SIZE_EXCEEDED} (matching the
 * admin-api contract), not as a generic 400 VALIDATION_ERROR.
 *
 * <p>TASK-BE-635: each {@code accountIds} element is bounded to
 * {@link ClientIdempotencyKey#BULK_LOCK_ACCOUNT_ID_MAX} ({@code accounts.id VARCHAR(36)}) — together with the
 * header bound ({@link ClientIdempotencyKey#BULK_LOCK_HEADER_MAX}), the per-row audit/downstream key
 * ({@code header + ":" + accountId}) fits {@code admin_actions.idempotency_key VARCHAR(100)} for every element,
 * not only the widest-looking one.
 */
public record BulkLockRequest(
        @NotNull @NotEmpty List<@Size(max = ClientIdempotencyKey.BULK_LOCK_ACCOUNT_ID_MAX,
                message = "accountId must be ≤36 characters") String> accountIds,
        @NotNull @Size(min = 8, message = "must be at least 8 characters") String reason,
        String ticketId
) {}
