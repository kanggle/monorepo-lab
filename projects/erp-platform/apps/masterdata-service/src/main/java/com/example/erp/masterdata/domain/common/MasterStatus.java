package com.example.erp.masterdata.domain.common;

/**
 * Master aggregate lifecycle status (erp E1 — logical retire only; physical
 * delete is structurally forbidden). All 5 masterdata aggregates share this
 * binary state (ACTIVE → RETIRED, terminal).
 *
 * <p>architecture.md § Aggregate lifecycles. Pure Java.
 */
public enum MasterStatus {
    ACTIVE,
    RETIRED
}
