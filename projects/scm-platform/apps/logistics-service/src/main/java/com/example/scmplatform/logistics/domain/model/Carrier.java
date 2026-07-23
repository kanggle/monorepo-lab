package com.example.scmplatform.logistics.domain.model;

/**
 * The dispatch <b>vendor</b> (aggregator) a shipment was pushed through — recorded on the
 * {@code dispatch.vendor} column and in the dedup snapshot so a shipment cannot be
 * double-dispatched across vendors (external-integrations.md §2.7).
 *
 * <p>Phase 1 (ADR-053 §D7) uses {@link #EASYPOST} for real dispatch and {@link #STANDALONE}
 * for the credential-free local/CI stub. {@link #GOODSFLOW} is the domestic aggregator whose
 * adapter lands in TASK-SCM-BE-043 — declared here (not routed) so the vendor enum is stable.
 * There is <b>no {@code CarrierRouter}</b> in this slice: with one live vendor the use case
 * dispatches directly.
 */
public enum Carrier {
    EASYPOST,
    GOODSFLOW,
    STANDALONE
}
