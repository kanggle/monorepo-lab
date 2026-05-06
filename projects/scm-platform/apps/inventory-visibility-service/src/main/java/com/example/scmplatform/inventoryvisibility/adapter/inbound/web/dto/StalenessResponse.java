package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;

import java.time.Instant;

public record StalenessResponse(
        String nodeId,
        String stalenessStatus,
        Instant lastEventAt,
        Instant lastCheckedAt
) {
    public static StalenessResponse from(NodeStaleness ns) {
        return new StalenessResponse(
                ns.getNodeId().toString(),
                ns.getStalenessStatus().name(),
                ns.getLastEventAt(),
                ns.getLastCheckedAt()
        );
    }
}
