package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The tenant-gate policy matrix.
 *
 * <p>Each domain's own suite asserts <em>which</em> policy its gateway wires (built from its
 * production {@code OAuth2ResourceServerConfig#tenantGate()}). This one asserts that each
 * policy <em>means</em> what it is supposed to mean — including the combinations no domain
 * currently uses, because a flag that is never exercised is a flag nobody can trust the day
 * someone flips it.
 */
@DisplayName("TenantClaimValidator — 테넌트 게이트 정책 매트릭스")
class TenantClaimValidatorTest {

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

    private static boolean accepts(TenantClaimValidator v, Object tenantId, Object entitled) {
        OAuth2TokenValidatorResult r = v.validate(jwt(tenantId, entitled));
        return !r.hasErrors();
    }

    @Nested
    @DisplayName("기본값은 최대한 닫혀 있다")
    class DefaultsAreClosed {

        private final TenantClaimValidator strict = TenantClaimValidator.forTenant("wms").build();

        @Test
        @DisplayName("정확히 일치하는 tenant_id 만 통과")
        void onlyExactMatchPasses() {
            assertThat(accepts(strict, "wms", null)).isTrue();
            assertThat(accepts(strict, "scm", null)).isFalse();
        }

        @Test
        @DisplayName("wildcard 는 명시 허용 없이는 거부된다 — 공유 보안 클래스의 기본값이 게이트를 열면 안 된다")
        void wildcardRejectedUnlessOptedIn() {
            assertThat(accepts(strict, TenantClaimValidator.WILDCARD_TENANT, null)).isFalse();
        }

        @Test
        @DisplayName("entitled_domains 는 명시 신뢰 없이는 무시된다")
        void entitlementIgnoredUnlessOptedIn() {
            assertThat(accepts(strict, "scm", List.of("wms"))).isFalse();
        }
    }

    /** wms: strict equality, no wildcard, entitlement dual-accept (ADR-MONO-048 § D5). */
    @Nested
    @DisplayName("wms 정책 — 엄격 일치 + entitlement, wildcard 없음")
    class WmsPolicy {

        private final TenantClaimValidator v =
                TenantClaimValidator.forTenant("wms").trustEntitledDomains().build();

        @Test
        void exactTenantPasses() {
            assertThat(accepts(v, "wms", null)).isTrue();
        }

        @Test
        void crossTenantRejected() {
            assertThat(accepts(v, "fan-platform", null)).isFalse();
        }

        @Test
        @DisplayName("wildcard 거부 — scm/fan 과 갈리는 지점이며 의도된 선택이다")
        void wildcardRejected() {
            assertThat(accepts(v, TenantClaimValidator.WILDCARD_TENANT, null)).isFalse();
        }

        @Test
        @DisplayName("entitled_domains=[wms] 는 tenant_id 불일치를 극복한다")
        void entitlementGrantsAccessDespiteCrossTenant() {
            assertThat(accepts(v, "fan-platform", List.of("wms"))).isTrue();
        }

        @Test
        void entitlementForAnotherDomainDoesNotHelp() {
            assertThat(accepts(v, "fan-platform", List.of("scm"))).isFalse();
        }
    }

    /** scm: equality + wildcard + entitlement. */
    @Nested
    @DisplayName("scm 정책 — 일치 + wildcard + entitlement")
    class ScmPolicy {

        private final TenantClaimValidator v = TenantClaimValidator.forTenant("scm")
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();

        @Test
        void exactTenantPasses() {
            assertThat(accepts(v, "scm", null)).isTrue();
        }

        @Test
        @DisplayName("wildcard 통과 — SUPER_ADMIN 장애 대응")
        void wildcardPasses() {
            assertThat(accepts(v, TenantClaimValidator.WILDCARD_TENANT, null)).isTrue();
        }

        @Test
        void entitlementGrantsAccess() {
            assertThat(accepts(v, "wms", List.of("scm"))).isTrue();
        }

        @Test
        void crossTenantWithoutEntitlementRejected() {
            assertThat(accepts(v, "wms", null)).isFalse();
        }
    }

    /**
     * fan: equality + wildcard, <strong>no entitlement</strong>. fan is outside the
     * entitlement plane, so the branch would be dead code in a production security filter.
     */
    @Nested
    @DisplayName("fan 정책 — 일치 + wildcard, entitlement 분기 없음")
    class FanPolicy {

        private final TenantClaimValidator v = TenantClaimValidator.forTenant("fan-platform")
                .allowSuperAdminWildcard()
                .build();

        @Test
        void exactTenantPasses() {
            assertThat(accepts(v, "fan-platform", null)).isTrue();
        }

        @Test
        void wildcardPasses() {
            assertThat(accepts(v, TenantClaimValidator.WILDCARD_TENANT, null)).isTrue();
        }

        @Test
        @DisplayName("entitled_domains=[fan-platform] 이어도 통과시키지 않는다 — 켜지지 않은 분기다")
        void entitlementIsNotConsulted() {
            assertThat(accepts(v, "wms", List.of("fan-platform"))).isFalse();
        }
    }

    @Nested
    @DisplayName("claim 이상 형태 — 전부 fail-closed")
    class MalformedClaims {

        private final TenantClaimValidator v =
                TenantClaimValidator.forTenant("wms").trustEntitledDomains().build();

        @Test
        void missingTenantRejected() {
            assertThat(accepts(v, null, null)).isFalse();
        }

        @Test
        void blankTenantRejected() {
            assertThat(accepts(v, "   ", null)).isFalse();
        }

        @Test
        @DisplayName("tenant_id 가 문자열이 아니면 (숫자 등) 부재로 취급")
        void nonStringTenantRejected() {
            assertThat(accepts(v, 42, null)).isFalse();
        }

        @Test
        @DisplayName("entitled_domains 에 비문자열 원소가 섞여도 예외 없이 '자격 없음' 으로 강등")
        void malformedEntitledDomainsFailsClosed() {
            assertThat(accepts(v, null, List.of(123))).isFalse();
        }

        @Test
        @DisplayName("entitled_domains 가 리스트가 아니면 무시")
        void nonListEntitledDomainsIgnored() {
            assertThat(accepts(v, "scm", "wms")).isFalse();
        }

        @Test
        @DisplayName("비문자열 원소가 섞여 있어도 유효한 원소는 계속 인정된다")
        void mixedEntitledDomainsStillHonoursStringElements() {
            assertThat(accepts(v, "scm", List.of(123, "wms"))).isTrue();
        }
    }

    @Nested
    @DisplayName("에러 코드와 메시지")
    class ErrorReporting {

        private final TenantClaimValidator v = TenantClaimValidator.forTenant("wms").build();

        @Test
        @DisplayName("거부는 tenant_mismatch — SecurityConfig 가 이 코드로 403 을 매핑한다 (401 이 아니라)")
        void raisesTenantMismatch() {
            OAuth2TokenValidatorResult r = v.validate(jwt("scm", null));
            assertThat(r.getErrors()).anyMatch(
                    e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
            assertThat(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH)
                    .isEqualTo(GatewayErrorCodes.TENANT_MISMATCH);
        }

        @Test
        @DisplayName("claim 부재와 claim 불일치는 서로 다른 메시지를 낸다")
        void distinguishesAbsentFromDisallowed() {
            assertThat(v.validate(jwt(null, null)).getErrors())
                    .anyMatch(e -> e.getDescription().contains("required"));
            assertThat(v.validate(jwt("scm", null)).getErrors())
                    .anyMatch(e -> e.getDescription().contains("'scm' is not allowed"));
        }
    }
}
