package com.wms.outbound.domain.model.masterref;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master Lot.
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
