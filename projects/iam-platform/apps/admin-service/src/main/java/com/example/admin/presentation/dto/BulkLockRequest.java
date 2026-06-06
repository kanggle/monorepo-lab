package com.example.admin.presentation.dto;

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
 */
public record BulkLockRequest(
        @NotNull @NotEmpty List<String> accountIds,
        @NotNull @Size(min = 8, message = "must be at least 8 characters") String reason,
        String ticketId
) {}
