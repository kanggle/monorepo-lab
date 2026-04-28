package com.wms.inbound.domain.model.masterref;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master Partner.
 *
 * <p>Populated by {@code MasterPartnerConsumer} from
 * {@code wms.master.partner.v1} events. Drives ASN supplier validation
 * ({@code PARTNER_INVALID_TYPE}).
 */
public record PartnerSnapshot(
        UUID id,
        String partnerCode,
        PartnerType partnerType,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public PartnerSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(partnerCode, "partnerCode");
        Objects.requireNonNull(partnerType, "partnerType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cachedAt, "cachedAt");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean canSupply() {
        return status == Status.ACTIVE
                && (partnerType == PartnerType.SUPPLIER || partnerType == PartnerType.BOTH);
    }

    public enum PartnerType {
        SUPPLIER,
        CARRIER,
        CUSTOMER,
        BOTH
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
