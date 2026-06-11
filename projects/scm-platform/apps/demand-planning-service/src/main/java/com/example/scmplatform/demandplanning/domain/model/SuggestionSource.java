package com.example.scmplatform.demandplanning.domain.model;

/**
 * How the reorder suggestion was triggered.
 */
public enum SuggestionSource {
    /** Triggered by a wms inventory.low-stock-detected alert. */
    ALERT,
    /** Triggered by the nightly ReorderSweepScheduler batch. */
    BATCH
}
