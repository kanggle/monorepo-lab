package com.example.account.application.result;

import java.util.List;

/**
 * TASK-BE-257: Result of a bulk provisioning operation.
 *
 * <p>Partial-success model: {@code created} holds rows that succeeded;
 * {@code failed} holds rows that produced a per-row error.
 *
 * @param created  successfully created accounts (one entry per committed row)
 * @param failed   rows that failed (one entry per rejected row)
 * @param summary  aggregate counts
 */
public record BulkProvisionAccountResult(
        List<CreatedItem> created,
        List<FailedItem> failed,
        Summary summary
) {

    /**
     * Per-row success entry.
     *
     * @param externalId  the caller-supplied dedup key (may be null)
     * @param accountId   the newly assigned account UUID
     */
    public record CreatedItem(
            String externalId,
            String accountId
    ) {}

    /**
     * Per-row failure entry.
     *
     * @param externalId  the caller-supplied dedup key (may be null)
     * @param errorCode   machine-readable error code (e.g. EMAIL_DUPLICATE)
     * @param message     human-readable description
     */
    public record FailedItem(
            String externalId,
            String errorCode,
            String message
    ) {}

    /**
     * Aggregate summary for the bulk call.
     */
    public record Summary(
            int requested,
            int created,
            int failed
    ) {}

    public static BulkProvisionAccountResult of(List<CreatedItem> created, List<FailedItem> failed,
                                                 int requested) {
        Summary summary = new Summary(requested, created.size(), failed.size());
        return new BulkProvisionAccountResult(created, failed, summary);
    }
}
