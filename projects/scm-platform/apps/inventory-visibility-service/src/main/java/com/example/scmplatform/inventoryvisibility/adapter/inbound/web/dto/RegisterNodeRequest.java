package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

/**
 * Request body for {@code POST /api/inventory-visibility/nodes}
 * (TASK-SCM-BE-046 — explicit THIRD_PARTY_LOGISTICS node registration).
 */
public record RegisterNodeRequest(String nodeExternalId, String name) {
}
