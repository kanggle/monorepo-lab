package com.example.scmplatform.inventoryvisibility.domain.staleness;

/**
 * Current freshness status of a node's snapshot data.
 * <p>
 * FRESH — last event received within staleness threshold.
 * STALE — no event received within the threshold period.
 * UNREACHABLE — node has never reported any event (registered but no events yet).
 */
public enum StalenessStatus {
    FRESH,
    STALE,
    UNREACHABLE
}
