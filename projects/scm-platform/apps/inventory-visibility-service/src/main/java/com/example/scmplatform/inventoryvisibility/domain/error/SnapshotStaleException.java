package com.example.scmplatform.inventoryvisibility.domain.error;

/**
 * S5 — raised when a snapshot's data exceeds the staleness threshold.
 * Error code: SNAPSHOT_STALE (scm.md Standard Error Codes).
 */
public class SnapshotStaleException extends RuntimeException {
    public SnapshotStaleException(String nodeId) {
        super("Inventory snapshot is stale for node: " + nodeId);
    }
}
