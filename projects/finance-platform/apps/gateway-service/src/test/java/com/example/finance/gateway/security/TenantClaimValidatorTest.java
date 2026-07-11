package com.example.finance.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.security.TenantClaimValidator;
import com.example.finance.gateway.config.OAuth2ResourceServerConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * finance's tenant-gate policy, asserted against the gate the production config actually builds.
 *
 * <p>Both halves are asserted on purpose. TASK-MONO-355 found that every domain suite in the
 * fleet pinned what its gate <em>accepts</em> and none pinned what it <em>refuses</em> — so wms's
 * rejection of the SUPER_ADMIN wildcard, the single most distinctive gate in the fleet, had zero
 * coverage and could have been opened by one added line in silence. A gate is defined as much by
 * what it turns away.
 */
@DisplayName("TenantClaimValidator — finance 게이트 정책 (gateway)")
class TenantClaimValidatorTest {

    // Built from the production wiring, not hand-constructed: change the gate in
    // OAuth2ResourceServerConfig#tenantGate and these assertions go red (TASK-MONO-357).
    private final TenantClaimValidator validator = new OAuth2ResourceServerConfig(
            "http://iam.local/oauth2/jwks", "http://iam.local,iam", "finance").tenantGate();

    private static Jwt jwt(Object tenantId, Object entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenantId != null) {
            b.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            b.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return b.build();
    }

    private boolean accepts(Object tenantId, Object entitled) {
        OAuth2TokenValidatorResult r = validator.validate(jwt(tenantId, entitled));
        return !r.hasErrors();
    }

    // --- what the gate accepts ---

    @Test
    @DisplayName("tenant_id=finance → success")
    void financeTenantPasses() {
        assertThat(accepts("finance", null)).isTrue();
    }

    @Test
    @DisplayName("tenant_id=* (SUPER_ADMIN platform-scope) → success")
    void superAdminWildcardPasses() {
        assertThat(accepts(TenantClaimValidator.WILDCARD_TENANT, null)).isTrue();
    }

    @Test
    @DisplayName("entitled_domains=[finance] 는 tenant_id 불일치를 극복한다 (ADR-019 §D5 dual-accept)")
    void entitlementGrantsAccessDespiteCrossTenant() {
        assertThat(accepts("wms", List.of("finance"))).isTrue();
    }

    // --- what the gate refuses ---

    @Test
    @DisplayName("교차 테넌트 + 자격 없음 → tenant_mismatch")
    void crossTenantWithoutEntitlementRejected() {
        assertThat(accepts("wms", null)).isFalse();
        assertThat(validator.validate(jwt("wms", null)).getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("다른 도메인 자격은 도움이 되지 않는다")
    void entitlementForAnotherDomainDoesNotHelp() {
        assertThat(accepts("wms", List.of("erp"))).isFalse();
    }

    @Test
    @DisplayName("tenant_id 부재 / blank / 비문자열 → 거부 (fail-closed)")
    void malformedTenantClaimRejected() {
        assertThat(accepts(null, null)).isFalse();
        assertThat(accepts("   ", null)).isFalse();
        assertThat(accepts(42, null)).isFalse();
    }

    /**
     * The marketplace edge (ecommerce) admits any well-formed tenant. finance is a single-tenant
     * edge and must not — that switch is the only one in the library that opens a gate, and this
     * asserts it did not leak here.
     */
    @Test
    @DisplayName("아무 테넌트나 통과시키지 않는다 — accept-any 스위치는 마켓플레이스 엣지 전용이다")
    void doesNotAcceptAnyWellFormedTenant() {
        assertThat(validator.acceptsAnyWellFormedTenant()).isFalse();
        assertThat(accepts("some-stranger-tenant", null)).isFalse();
    }

    @Test
    @DisplayName("자격 클레임이 이상해도 예외 없이 '자격 없음' 으로 강등된다")
    void malformedEntitlementFailsClosed() {
        assertThat(accepts("wms", List.of(123))).isFalse();
        assertThat(accepts("wms", "finance")).isFalse();
    }
}
