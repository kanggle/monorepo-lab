package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyStatus;

import java.time.Instant;

/** Read model of an agency — {@code artist-api.md} § Agencies (TASK-MONO-748). */
public record AgencyView(
        String id,
        String tenantId,
        String name,
        AgencyStatus status,
        // ecommerce seller_id that sells this agency's goods (ADR-MONO-079 D2, 0..1); null = none.
        String storeSellerId,
        Instant createdAt,
        Instant updatedAt
) {

    public static AgencyView from(Agency a) {
        return new AgencyView(a.getId().value(), a.getTenantId(), a.getName(), a.getStatus(),
                a.getStoreSellerId(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
