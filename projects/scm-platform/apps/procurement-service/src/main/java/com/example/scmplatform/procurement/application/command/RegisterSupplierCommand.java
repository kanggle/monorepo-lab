package com.example.scmplatform.procurement.application.command;

import com.example.scmplatform.procurement.application.ActorContext;

import java.time.Instant;

/**
 * Register a supplier in the v1 internal master (TASK-SCM-BE-059 AC-2).
 *
 * <p>No credential field — v1 has no path that accepts supplier credentials
 * (ADR-SCM-001 ACCEPT rider). Do not add one here.
 */
public record RegisterSupplierCommand(
        ActorContext actor,
        String code,
        String name,
        Instant contractStartedAt,
        Instant contractExpiresAt
) {
}
