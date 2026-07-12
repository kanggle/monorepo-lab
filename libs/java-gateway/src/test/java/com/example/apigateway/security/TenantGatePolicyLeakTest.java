package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.oauth2.TenantClaimValidator;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@code acceptAnyWellFormedTenant} is the only switch in this library that <strong>opens</strong>
 * a gate rather than narrowing one. It exists for exactly one edge — ecommerce, the multi-tenant
 * marketplace (ADR-MONO-030 § 2.4) — and turning it on anywhere else would silently admit every
 * tenant's tokens to a gateway that serves one.
 *
 * <p>A switch like that needs a test that says <em>where it isn't</em>. Every other guard in this
 * suite asserts what a policy does; this one asserts what three policies must never become.
 *
 * <p>The three single-tenant policies are reconstructed here rather than imported, because
 * {@code libs/} may not depend on a service module. The domain suites assert that each gateway
 * really wires the policy named below (they build their validator from the production
 * {@code OAuth2ResourceServerConfig#tenantGate()}); this test asserts what that policy then means.
 */
@DisplayName("테넌트 게이트 — 문을 여는 스위치가 단일-테넌트 엣지로 새지 않는가")
class TenantGatePolicyLeakTest {

    private static final TenantClaimValidator WMS =
            TenantClaimValidator.forTenant("wms").trustEntitledDomains().build();
    private static final TenantClaimValidator SCM = TenantClaimValidator.forTenant("scm")
            .allowSuperAdminWildcard()
            .trustEntitledDomains()
            .build();
    private static final TenantClaimValidator FAN = TenantClaimValidator.forTenant("fan-platform")
            .allowSuperAdminWildcard()
            .build();
    private static final TenantClaimValidator ECOMMERCE =
            TenantClaimValidator.forTenant("ecommerce").acceptAnyWellFormedTenant().build();

    @Test
    @DisplayName("wms / scm / fan 은 accept-any 가 꺼져 있다")
    void singleTenantEdgesDoNotAcceptAnyTenant() {
        assertThat(WMS.acceptsAnyWellFormedTenant()).as("wms").isFalse();
        assertThat(SCM.acceptsAnyWellFormedTenant()).as("scm").isFalse();
        assertThat(FAN.acceptsAnyWellFormedTenant()).as("fan").isFalse();
    }

    @Test
    @DisplayName("ecommerce 만 켜져 있다")
    void onlyTheMarketplaceEdgeAcceptsAnyTenant() {
        assertThat(ECOMMERCE.acceptsAnyWellFormedTenant()).isTrue();
    }

    /**
     * The flag's meaning, stated as behaviour rather than as a getter: a stranger's tenant is
     * refused by the three single-tenant edges and admitted by the marketplace one.
     */
    @Test
    @DisplayName("낯선 테넌트 토큰: 단일-테넌트 엣지 3곳은 거부, 마켓플레이스만 허용")
    void aStrangersTenantIsRefusedEverywhereButTheMarketplace() {
        Jwt stranger = jwt("some-other-tenant");

        assertThat(WMS.validate(stranger).hasErrors()).as("wms").isTrue();
        assertThat(SCM.validate(stranger).hasErrors()).as("scm").isTrue();
        assertThat(FAN.validate(stranger).hasErrors()).as("fan").isTrue();
        assertThat(ECOMMERCE.validate(stranger).hasErrors()).as("ecommerce").isFalse();
    }

    /**
     * "Any tenant" is not "no tenant". A blank claim still fails at the marketplace edge — it would
     * leave the persistence-layer {@code WHERE tenant_id} filter with nothing to filter on, which is
     * the layer that actually holds ecommerce's tenant boundary (ADR-MONO-024 D2).
     */
    @Test
    @DisplayName("'아무 테넌트' 는 '테넌트 없음' 이 아니다 — blank 는 마켓플레이스에서도 거부")
    void blankTenantIsStillRefusedByTheMarketplace() {
        assertThat(ECOMMERCE.validate(jwt("   ")).hasErrors()).isTrue();
        assertThat(ECOMMERCE.validate(jwt(null)).hasErrors()).isTrue();
        assertThat(ECOMMERCE.validate(jwt(42)).hasErrors()).as("non-string claim").isTrue();
    }

    private static Jwt jwt(Object tenantId) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenantId != null) {
            b.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        return b.build();
    }
}
