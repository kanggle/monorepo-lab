package com.wms.inbound.domain.model;

public enum AsnSource {
    MANUAL,
    WEBHOOK_ERP,
    /**
     * Inbound expectation pre-created from an scm-platform confirmed purchase order
     * (ADR-MONO-050). The originating PO is traced via {@code Asn.poNumber} / {@code poId}.
     */
    SCM_PROCUREMENT
}
