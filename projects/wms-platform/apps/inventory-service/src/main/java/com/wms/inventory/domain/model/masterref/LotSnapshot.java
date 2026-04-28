package com.wms.inventory.domain.model.masterref;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master Lot.
 *
 * <p>Populated by {@code MasterLotConsumer} from {@code wms.master.lot.v1}
 * events. Lots flow through {@code ACTIVE → EXPIRED} via the master scheduler
 * and {@code ACTIVE → INACTIVE} via manual deactivate. Inventory mutations on
 * a non-{@code ACTIVE} lot are rejected at the use-case layer.
 */
public record LotSnapshot(
        UUID id,
        UUID skuId,
        String lotNo,
        LocalDate expiryDate,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public LotSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(lotNo, "lotNo");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cachedAt, "cachedAt");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isExpired() {
        return status == Status.EXPIRED;
    }

    public enum Status {
        ACTIVE,
        INACTIVE,
        EXPIRED
    }
}
