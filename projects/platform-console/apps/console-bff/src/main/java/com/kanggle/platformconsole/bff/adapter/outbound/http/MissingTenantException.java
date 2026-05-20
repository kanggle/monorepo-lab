package com.kanggle.platformconsole.bff.adapter.outbound.http;

/**
 * Thrown when {@code X-Tenant-Id} is absent on an inbound BFF request.
 *
 * <p>Per architecture.md § Multi-Tenant Isolation (D6.A): absent {@code X-Tenant-Id}
 * on inbound is fail-closed ({@code 400 NO_ACTIVE_TENANT}) before any outbound call —
 * matches the {@code console-web} server-route behaviour.
 */
public class MissingTenantException extends RuntimeException {

    public MissingTenantException() {
        super("X-Tenant-Id header is absent — dispatch fails closed (400 NO_ACTIVE_TENANT)");
    }
}
