package com.wms.master.config.security;

import com.example.security.oauth2.TenantClaimValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins master-service's tenant gate policy (TASK-MONO-390, ADR-MONO-049 § D5-7).
 *
 * <p>The subject is built from {@link OAuth2ResourceServerConfig#jwtTokenValidator()} — the
 * <em>production</em> chain — and not from a hand-written {@code TenantClaimValidator.forTenant(...)}
 * builder. A suite that builds its own chain stays green while the real gate changes underneath it,
 * which would make every assertion below theatre (TASK-MONO-355).
 *
 * <h2>The wildcard axis is the point of this file</h2>
 *
 * wms is the <strong>only</strong> platform that rejects the SUPER_ADMIN {@code "*"} wildcard.
 * The validator builder defaults closed, so <em>forgetting</em> a switch narrows the gate and
 * something goes red; <em>adding</em> one widens it and nothing does. {@code TASK-MONO-355} found
 * that this gate had <strong>zero coverage for its rejection</strong> — the copies this task deleted
 * asserted the tenant match, the cross-tenant refusal and the entitlement grant, but never once
 * asserted that {@code "*"} is refused. {@link TheWildcardIsRefused} is that missing assertion.
 */
@DisplayName("wms tenant gate 정책 핀 (master-service)")
class WmsTenantGatePolicyTest {

    private static final String TENANT = "wms";
    private static final String SAS_ISSUER = "http://localhost:8081";
    private static final String LEGACY_ISSUER = "iam";

    /** The production wiring, with only the {@code @Value} fields supplied. */
    private static OAuth2ResourceServerConfig wiredConfig(String allowedIssuersCsv) {
        OAuth2ResourceServerConfig config = new OAuth2ResourceServerConfig();
        ReflectionTestUtils.setField(config, "requiredTenantId", TENANT);
        ReflectionTestUtils.setField(config, "allowedIssuersCsv", allowedIssuersCsv);
        return config;
    }

    private final OAuth2TokenValidator<Jwt> gate =
            wiredConfig(SAS_ISSUER + "," + LEGACY_ISSUER).jwtTokenValidator();

    private static Jwt token(String issuer, Map<String, Object> claims) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(now.minus(1, ChronoUnit.MINUTES))
                .expiresAt(now.plus(10, ChronoUnit.MINUTES))
                .claim("iss", issuer)
                .subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static Jwt wmsToken(Map<String, Object> claims) {
        return token(SAS_ISSUER, claims);
    }

    private static List<String> errorCodes(OAuth2TokenValidatorResult result) {
        return result.getErrors().stream().map(OAuth2Error::getErrorCode).toList();
    }

    @Nested
    @DisplayName("tenant 축")
    class TenantAxis {

        @Test
        @DisplayName("tenant_id=wms → 통과")
        void wmsTenantPasses() {
            OAuth2TokenValidatorResult result =
                    gate.validate(wmsToken(Map.of("tenant_id", TENANT)));
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=fan-platform → tenant_mismatch")
        void crossTenantRejected() {
            OAuth2TokenValidatorResult result =
                    gate.validate(wmsToken(Map.of("tenant_id", "fan-platform")));
            assertThat(errorCodes(result))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("tenant_id 미존재 → tenant_mismatch (fail-closed)")
        void missingTenantRejected() {
            OAuth2TokenValidatorResult result = gate.validate(wmsToken(Map.of()));
            assertThat(errorCodes(result))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("tenant_id=blank → tenant_mismatch")
        void blankTenantRejected() {
            OAuth2TokenValidatorResult result =
                    gate.validate(wmsToken(Map.of("tenant_id", "  ")));
            assertThat(errorCodes(result))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }
    }

    @Nested
    @DisplayName("entitlement 축 — trustEntitledDomains() 가 켜져 있다")
    class EntitlementAxis {

        @Test
        @DisplayName("entitled_domains=[wms] + tenant_id=fan-platform → 통과 (entitlement 가 연다)")
        void entitledDomainGrantsAccessDespiteCrossTenant() {
            OAuth2TokenValidatorResult result = gate.validate(wmsToken(Map.of(
                    "tenant_id", "fan-platform",
                    "entitled_domains", List.of(TENANT))));
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("entitled_domains=[scm] + tenant_id=fan-platform → tenant_mismatch (다른 도메인은 못 연다)")
        void entitledDomainForOtherDomainDoesNotHelp() {
            OAuth2TokenValidatorResult result = gate.validate(wmsToken(Map.of(
                    "tenant_id", "fan-platform",
                    "entitled_domains", List.of("scm"))));
            assertThat(errorCodes(result))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("entitled_domains 가 malformed + tenant_id 없음 → fail-closed")
        void malformedEntitledDomainsFailsClosed() {
            OAuth2TokenValidatorResult result = gate.validate(wmsToken(Map.of(
                    "entitled_domains", List.of(42, true))));
            assertThat(errorCodes(result))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        /**
         * Removing {@code .trustEntitledDomains()} from the production config turns
         * {@link #entitledDomainGrantsAccessDespiteCrossTenant()} red. This test states the
         * switch is ON in the affirmative, so the mutation is unambiguous in both directions.
         */
        @Test
        @DisplayName("entitlement 만으로 열린다 — tenant_id 가 아예 없어도")
        void entitlementAloneAdmits() {
            OAuth2TokenValidatorResult result = gate.validate(wmsToken(Map.of(
                    "entitled_domains", List.of(TENANT))));
            assertThat(result.hasErrors()).isFalse();
        }
    }

    /**
     * AC-4. The assertion that did not exist before this task.
     *
     * <p>Add {@code .allowSuperAdminWildcard()} to {@link OAuth2ResourceServerConfig} and this
     * group goes red — which is the whole point, because that mistake would otherwise open the
     * wms gate to any platform-scoped token and no existing test would have noticed.
     */
    @Nested
    @DisplayName("와일드카드 축 — wms 는 SUPER_ADMIN '*' 를 거부한다 (AC-4)")
    class TheWildcardIsRefused {

        @Test
        @DisplayName("tenant_id=* → tenant_mismatch (다른 플랫폼은 통과시키는 그 토큰)")
        void superAdminWildcardIsRejected() {
            OAuth2TokenValidatorResult result =
                    gate.validate(wmsToken(Map.of("tenant_id", TenantClaimValidator.WILDCARD_TENANT)));
            assertThat(errorCodes(result))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        /**
         * A {@code "*"} token is not admitted <em>by the wildcard</em> even when it is admitted —
         * the entitlement claim is what opens the gate here. Pinning this keeps a future reader
         * from concluding the wildcard "works" and switching it on.
         */
        @Test
        @DisplayName("tenant_id=* + entitled_domains=[wms] → 통과하되, 여는 것은 entitlement 이지 와일드카드가 아니다")
        void wildcardTokenIsAdmittedOnlyViaEntitlement() {
            OAuth2TokenValidatorResult result = gate.validate(wmsToken(Map.of(
                    "tenant_id", TenantClaimValidator.WILDCARD_TENANT,
                    "entitled_domains", List.of(TENANT))));
            assertThat(result.hasErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("issuer 축")
    class IssuerAxis {

        @Test
        @DisplayName("SAS 발급자 → 통과")
        void sasIssuerPasses() {
            OAuth2TokenValidatorResult result =
                    gate.validate(token(SAS_ISSUER, Map.of("tenant_id", TENANT)));
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("legacy 발급자 'iam' → 통과 (D2-b 유예 중)")
        void legacyIssuerPasses() {
            OAuth2TokenValidatorResult result =
                    gate.validate(token(LEGACY_ISSUER, Map.of("tenant_id", TENANT)));
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("허용되지 않은 발급자 → invalid_issuer")
        void unknownIssuerRejected() {
            OAuth2TokenValidatorResult result =
                    gate.validate(token("https://evil.example.com", Map.of("tenant_id", TENANT)));
            assertThat(errorCodes(result)).contains("invalid_issuer");
        }

        /**
         * An empty allowlist is a misconfiguration, and it fails at startup rather than
         * degrading into a gate that accepts every issuer.
         */
        @Test
        @DisplayName("allowed-issuers 가 비면 기동이 실패한다 — 게이트가 열리지 않는다")
        void emptyAllowlistFailsAtStartup() {
            assertThatThrownBy(() -> wiredConfig("").jwtTokenValidator())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * The 403 {@code TENANT_FORBIDDEN} envelope that {@code SecurityConfig}'s
     * {@code AuthenticationEntryPoint} raises is keyed off this error code, and
     * {@code specs/integration/iam-integration.md} states it as a contract. If the shared class
     * ever changed the code, cross-tenant rejections would silently degrade from 403 to 401.
     */
    @Test
    @DisplayName("에러코드는 계약이다 — tenant_mismatch (403 TENANT_FORBIDDEN 이 여기에 걸려 있다)")
    void theErrorCodeIsAContract() {
        assertThat(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH).isEqualTo("tenant_mismatch");
    }
}
