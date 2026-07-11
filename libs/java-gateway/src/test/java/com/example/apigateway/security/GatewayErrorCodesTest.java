package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire value of the cross-boundary error code.
 *
 * <p>This is not busywork. The library's {@code SecurityConfig} maps this code to 403
 * {@code TENANT_FORBIDDEN}; the per-domain {@code TenantClaimValidator} is what
 * <em>raises</em> it. If the two ever disagreed on the literal, the mapping would fail
 * open to a 401 — silently telling a cross-tenant client to re-authenticate, which is a
 * lie it can loop on forever. Each domain's validator now delegates its public constant
 * to this one, so they cannot drift; this test pins the value they share.
 */
@DisplayName("GatewayErrorCodes — cross-boundary wire values")
class GatewayErrorCodesTest {

    @Test
    void tenantMismatchWireValueIsPinned() {
        assertThat(GatewayErrorCodes.TENANT_MISMATCH).isEqualTo("tenant_mismatch");
    }
}
