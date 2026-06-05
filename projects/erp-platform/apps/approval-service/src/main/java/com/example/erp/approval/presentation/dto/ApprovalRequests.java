package com.example.erp.approval.presentation.dto;

import com.example.erp.approval.domain.error.ApprovalErrors.ValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

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

    /**
     * POST /requests — create DRAFT. v2.0 (TASK-ERP-BE-012): accepts an ordered
     * {@code approverIds} (1~N stages) OR the legacy {@code approverId} (a 1-stage
     * route); exactly one is required. The platform-console (PC-FE-051) keeps
     * sending {@code approverId} and continues to work unchanged.
     */
    public record CreateRequest(
            @NotBlank String subjectType,
            @NotBlank String subjectId,
            @NotBlank @Size(max = 256) String title,
            @Size(max = 512) String reason,
            String approverId,
            List<String> approverIds) {

        /**
         * Resolve the ordered stage approver ids from exactly one of the two
         * mutually-exclusive fields. Empty/both/neither → 400 VALIDATION_ERROR.
         */
        public List<String> resolveApproverIds() {
            boolean hasSingle = approverId != null && !approverId.isBlank();
            boolean hasList = approverIds != null && !approverIds.isEmpty();
            if (hasSingle == hasList) {
                throw new ValidationException(
                        "exactly one of 'approverId' (single-stage) or 'approverIds' "
                                + "(multi-stage) is required");
            }
            return hasSingle ? List.of(approverId) : approverIds;
        }
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
