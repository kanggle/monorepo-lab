package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.oauth2.TenantClaimValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire value of the cross-boundary error code, and pins the two sides together.
 *
 * <p>This is not busywork. The library's {@code SecurityConfig} maps this code to 403
 * {@code TENANT_FORBIDDEN}; {@link TenantClaimValidator} is what <em>raises</em> it. If the
 * two ever disagreed on the literal, the mapping would fail open to a 401 — silently telling
 * a cross-tenant client to re-authenticate, which is a lie it can loop on forever.
 *
 * <p><strong>The agreement is now a cross-module claim, which is why the assertion lives
 * here.</strong> It used to sit in {@code TenantClaimValidatorTest}, back when both classes
 * were in this module. That test moved to {@code libs/java-security} with its subject
 * (ADR-MONO-049 § D5-1), and from there it <em>cannot see</em> {@code GatewayErrorCodes} —
 * {@code java-gateway} depends on {@code java-security}, not the other way round. Left where
 * it was, the assertion would simply have been deleted to make the compile pass, and the
 * drift guard would have died in the move. It is asserted from this side instead.
 */
@DisplayName("GatewayErrorCodes — cross-boundary wire values")
class GatewayErrorCodesTest {

    @Test
    @DisplayName("the wire value is pinned — a rename here is a wire break")
    void tenantMismatchWireValueIsPinned() {
        assertThat(GatewayErrorCodes.TENANT_MISMATCH).isEqualTo("tenant_mismatch");
    }

    @Test
    @DisplayName("the code the gateway MAPS and the code the validator RAISES are the same string")
    void tenantMismatchAgreesWithTheValidatorThatRaisesIt() {
        assertThat(GatewayErrorCodes.TENANT_MISMATCH)
                .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }
}
