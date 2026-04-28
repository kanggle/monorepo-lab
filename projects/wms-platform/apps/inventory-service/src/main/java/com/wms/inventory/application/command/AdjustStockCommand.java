package com.wms.inventory.application.command;

import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import java.util.Set;
import java.util.UUID;

/**
 * Manual adjustment on a single Inventory bucket. The application service
 * decides which {@link AdjustOperation operation} variant to execute:
 * {@code REGULAR} (signed delta on any bucket), {@code MARK_DAMAGED}
 * (AVAILABLE → DAMAGED), or {@code WRITE_OFF_DAMAGED} (DAMAGED -= qty).
 *
 * <p>{@code callerRoles} carries the authenticated caller's granted authority
 * names (e.g. {@code "ROLE_INVENTORY_WRITE"}, {@code "ROLE_INVENTORY_ADMIN"})
 * so the application service can apply policy decisions that depend on roles
 * without coupling to {@code SecurityContextHolder} or any framework type.
 * Adapters are responsible for populating it from
 * {@link org.springframework.security.core.Authentication#getAuthorities()}
 * (or the equivalent in non-HTTP contexts). Never null — pass an empty set if
 * no roles apply.
 */
public record AdjustStockCommand(
        AdjustOperation operation,
        UUID inventoryId,
        Bucket bucket,
        int delta,
        ReasonCode reasonCode,
        String reasonNote,
        String actorId,
        String idempotencyKey,
        Set<String> callerRoles
) {

    public AdjustStockCommand {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        if (inventoryId == null) {
            throw new IllegalArgumentException("inventoryId is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        callerRoles = callerRoles == null ? Set.of() : Set.copyOf(callerRoles);
    }

    /**
     * Backward-compatible constructor for callers that don't carry role
     * information (legacy tests, internal pathways). Defaults
     * {@code callerRoles} to an empty set, which means any role-conditional
     * guard in the service will deny.
     */
    public AdjustStockCommand(
            AdjustOperation operation,
            UUID inventoryId,
            Bucket bucket,
            int delta,
            ReasonCode reasonCode,
            String reasonNote,
            String actorId,
            String idempotencyKey) {
        this(operation, inventoryId, bucket, delta, reasonCode, reasonNote,
                actorId, idempotencyKey, Set.of());
    }

    public enum AdjustOperation {
        REGULAR,
        MARK_DAMAGED,
        WRITE_OFF_DAMAGED
    }
}
