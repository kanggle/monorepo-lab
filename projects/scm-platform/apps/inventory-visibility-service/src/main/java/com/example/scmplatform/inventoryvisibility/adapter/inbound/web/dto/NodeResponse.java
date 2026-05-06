package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;

public record NodeResponse(
        String id,
        String nodeExternalId,
        String nodeType,
        String name,
        String status
) {
    public static NodeResponse from(InventoryNode node) {
        return new NodeResponse(
                node.getId().toString(),
                node.getNodeExternalId(),
                node.getNodeType().name(),
                node.getName(),
                node.getStatus().name()
        );
    }
}
