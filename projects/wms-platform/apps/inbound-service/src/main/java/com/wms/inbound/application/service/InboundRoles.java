package com.wms.inbound.application.service;

/**
 * Package-private constants for inbound-service role names and special actors.
 * Consolidates the per-service duplicate constants removed by U3 refactoring.
 */
final class InboundRoles {

    static final String ROLE_INBOUND_WRITE = "ROLE_INBOUND_WRITE";
    static final String ROLE_INBOUND_ADMIN = "ROLE_INBOUND_ADMIN";

    /** Actor prefix used by the ERP webhook integration pathway. */
    static final String SYSTEM_ACTOR_PREFIX = "system:";

    private InboundRoles() {
        // constants class — no instances
    }
}
