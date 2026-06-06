package com.example.account.presentation.dto.response;

import com.example.account.application.result.BulkProvisionAccountResult;

import java.util.List;

/**
 * TASK-BE-257: Response DTO for POST /internal/tenants/{tenantId}/accounts:bulk.
 *
 * <p>Always returned with HTTP 200. The caller must inspect {@code summary.failed}
 * to detect partial failures.
 *
 * @param created  per-row success entries
 * @param failed   per-row failure entries
 * @param summary  aggregate counts
 */
public record BulkProvisionAccountResponse(
        List<BulkProvisionAccountCreated> created,
        List<BulkProvisionAccountFailed> failed,
        BulkProvisionAccountSummary summary
) {

    /** Per-row success: maps the caller's externalId to the assigned accountId. */
    public record BulkProvisionAccountCreated(
            String externalId,
            String accountId
    ) {}

    /** Per-row failure: carries the caller's externalId plus a machine-readable error. */
    public record BulkProvisionAccountFailed(
            String externalId,
            String errorCode,
            String message
    ) {}

    /** Aggregate counts for the bulk call. */
    public record BulkProvisionAccountSummary(
            int requested,
            int created,
            int failed
    ) {}

    public static BulkProvisionAccountResponse from(BulkProvisionAccountResult result) {
        List<BulkProvisionAccountCreated> created = result.created().stream()
                .map(c -> new BulkProvisionAccountCreated(c.externalId(), c.accountId()))
                .toList();

        List<BulkProvisionAccountFailed> failed = result.failed().stream()
                .map(f -> new BulkProvisionAccountFailed(f.externalId(), f.errorCode(), f.message()))
                .toList();

        BulkProvisionAccountSummary summary = new BulkProvisionAccountSummary(
                result.summary().requested(),
                result.summary().created(),
                result.summary().failed()
        );

        return new BulkProvisionAccountResponse(created, failed, summary);
    }
}
