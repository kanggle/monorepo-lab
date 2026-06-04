package com.example.erp.approval.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTOs for approval-service endpoints (approval-api.md § Endpoints).
 * Bean-validation failures → 400 {@code VALIDATION_ERROR}. The required-reason
 * rule on reject/withdraw is enforced in the domain (so the contract's
 * {@code reason missing/blank → VALIDATION_ERROR} is honored uniformly), the
 * {@code @NotBlank} here is a fast-fail first line.
 */
public final class ApprovalRequests {

    private ApprovalRequests() {
    }

    /** POST /requests — create DRAFT. */
    public record CreateRequest(
            @NotBlank String subjectType,
            @NotBlank String subjectId,
            @NotBlank @Size(max = 256) String title,
            @Size(max = 512) String reason,
            @NotBlank String approverId) {
    }

    /** POST /requests/{id}/approve — reason optional. */
    public record ApproveRequest(@Size(max = 512) String reason) {
    }

    /** POST /requests/{id}/reject — reason required (E4). */
    public record RejectRequest(@NotBlank @Size(max = 512) String reason) {
    }

    /** POST /requests/{id}/withdraw — reason required (E4). */
    public record WithdrawRequest(@NotBlank @Size(max = 512) String reason) {
    }
}
