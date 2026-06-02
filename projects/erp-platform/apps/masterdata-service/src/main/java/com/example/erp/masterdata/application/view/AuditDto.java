package com.example.erp.masterdata.application.view;

import java.time.Instant;

/**
 * Presentation view of the audit timestamps
 * (masterdata-api.md § Common shapes — {@code Audit}, in detail responses).
 *
 * <p>Serialises as the nested {@code { "createdAt": "<ISO-8601>",
 * "updatedAt": "<ISO-8601>" }} (TASK-ERP-BE-006). The contract {@code Audit}
 * also lists {@code createdBy}/{@code updatedBy} actor fields, but the
 * masterdata entities do not track an actor on the row today — the available
 * subset is emitted, which the consumer {@code AuditSchema}
 * ({@code .partial().passthrough()}) tolerates. Actor enrichment is a separate
 * task if ever required.
 */
public record AuditDto(Instant createdAt, Instant updatedAt) {
}
