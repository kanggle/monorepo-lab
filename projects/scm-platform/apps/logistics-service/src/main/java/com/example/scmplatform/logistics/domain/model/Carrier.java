package com.example.scmplatform.logistics.domain.model;

/**
 * The dispatch <b>vendor</b> (aggregator) a shipment was pushed through — recorded on the
 * {@code dispatch.vendor} column and in the dedup snapshot so a shipment cannot be
 * double-dispatched across vendors (external-integrations.md §2.7).
 *
 * <p>Phase 1 (ADR-053 §D7) uses {@link #EASYPOST} for international dispatch and {@link #GOODSFLOW}
 * (TASK-SCM-BE-043) for domestic (KR) dispatch, with {@link #STANDALONE} the credential-free
 * local/CI stub. The {@code CarrierRouter} (BE-043) selects exactly one vendor per shipment from
 * the shipment's {@code requestedCarrierCode}; under the {@code standalone} profile the router
 * degrades to the single stub.
 */
public enum Carrier {
    EASYPOST,
    GOODSFLOW,
    STANDALONE
}
