package com.example.scmplatform.inventoryvisibility.domain.node;

/**
 * Classification of an inventory node in the supply chain.
 * <p>
 * v1: WMS_WAREHOUSE nodes receive events from wms-platform.
 * SUPPLIER / THIRD_PARTY_LOGISTICS / IN_TRANSIT are registered but have no
 * active event adapters in v1 (see Out of Scope in TASK-SCM-BE-003).
 */
public enum NodeType {
    WMS_WAREHOUSE,
    SUPPLIER,
    THIRD_PARTY_LOGISTICS,
    IN_TRANSIT
}
