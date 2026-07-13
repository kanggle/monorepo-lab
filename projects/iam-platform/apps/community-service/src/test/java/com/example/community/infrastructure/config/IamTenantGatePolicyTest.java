package com.example.community.infrastructure.config;

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
 * Pins community-service's tenant gate policy (TASK-MONO-392, ADR-MONO-049 § D5-8).
 *
 * <p>The subject comes from {@link OAuth2ResourceServerConfig#jwtTokenValidator()} — the chain
 * production actually wires — not from a hand-written builder. A suite that builds its own chain
 * stays green while the real gate changes underneath it (TASK-MONO-355), which is exactly the
 * defect this task removed from the slice tests and the integration base.
 *
 * <h2>iam is the strictest gate in the fleet, and this file is why that survives</h2>
 *
 * iam is the <strong>only</strong> project that calls <em>neither</em>
 * {@code allowSuperAdminWildcard()} <em>nor</em> {@code trustEntitledDomains()}. Because the
 * builder defaults every switch closed, <strong>"forgot a switch" is not a reachable mistake
 * here — there is nothing to forget.</strong> The only way to be wrong is to <em>add</em> one out
 * of habit, and adding <strong>widens</strong> the gate: a narrowed gate reds a test, a widened
 * one just quietly lets more through.
 *
 * <p>So both refusals are asserted — {@link TheWildcardIsRefused} and
 * {@link EntitlementIsRefused}. Either habit turns this suite red.
 */
@DisplayName("iam tenant gate 정책 핀 (community-service)")
class IamTenantGatePolicyTest {

    private static final String TENANT = "fan-platform";
    private static final String SAS_ISSUER = "http://localhost:8081";
    private static final String LEGACY_ISSUER = "iam";

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

    private static Jwt sasToken(Map<String, Object> claims) {
        return token(SAS_ISSUER, claims);
    }

    private static List<String> errorCodes(OAuth2TokenValidatorResult result) {
        return result.getErrors().stream().map(OAuth2Error::getErrorCode).toList();
    }

    @Nested
    @DisplayName("tenant 축")
    class TenantAxis {

        @Test
        @DisplayName("tenant_id=fan-platform → 통과")
        void expectedTenantPasses() {
            assertThat(gate.validate(sasToken(Map.of("tenant_id", TENANT))).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=wms → tenant_mismatch")
        void crossTenantRejected() {
            assertThat(errorCodes(gate.validate(sasToken(Map.of("tenant_id", "wms")))))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("tenant_id 미존재 → tenant_mismatch (fail-closed)")
        void missingTenantRejected() {
            assertThat(errorCodes(gate.validate(sasToken(Map.of()))))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("tenant_id=blank → tenant_mismatch")
        void blankTenantRejected() {
            assertThat(errorCodes(gate.validate(sasToken(Map.of("tenant_id", "  ")))))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }
    }

    /**
     * AC-4, refusal 1. Add {@code .allowSuperAdminWildcard()} to
     * {@link OAuth2ResourceServerConfig} and this group goes red.
     */
    @Nested
    @DisplayName("와일드카드 축 — iam 은 SUPER_ADMIN '*' 를 거부한다 (AC-4)")
    class TheWildcardIsRefused {

        @Test
        @DisplayName("tenant_id=* → tenant_mismatch (erp·fan·finance·scm 은 통과시키는 그 토큰)")
        void superAdminWildcardIsRejected() {
            assertThat(errorCodes(gate.validate(
                    sasToken(Map.of("tenant_id", TenantClaimValidator.WILDCARD_TENANT)))))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }
    }

    /**
     * AC-4, refusal 2. Add {@code .trustEntitledDomains()} and this group goes red.
     *
     * <p>iam sits outside the entitlement plane entirely: a signed {@code entitled_domains} claim
     * naming this tenant does <strong>not</strong> open the gate. Only {@code tenant_id} does.
     */
    @Nested
    @DisplayName("entitlement 축 — iam 은 entitled_domains 를 신뢰하지 않는다 (AC-4)")
    class EntitlementIsRefused {

        @Test
        @DisplayName("entitled_domains=[fan-platform] 만 있고 tenant_id 없음 → tenant_mismatch")
        void entitlementAloneDoesNotAdmit() {
            assertThat(errorCodes(gate.validate(sasToken(Map.of(
                    "entitled_domains", List.of(TENANT))))))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("entitled_domains=[fan-platform] + tenant_id=wms → tenant_mismatch (entitlement 이 크로스테넌트를 구제하지 않는다)")
        void entitlementDoesNotRescueCrossTenant() {
            assertThat(errorCodes(gate.validate(sasToken(Map.of(
                    "tenant_id", "wms",
                    "entitled_domains", List.of(TENANT))))))
                    .contains(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
        }

        @Test
        @DisplayName("tenant_id=fan-platform 이면 entitled_domains 유무와 무관하게 통과 — 여는 것은 tenant_id 다")
        void tenantIdAloneIsWhatAdmits() {
            assertThat(gate.validate(sasToken(Map.of(
                    "tenant_id", TENANT,
                    "entitled_domains", List.of("wms")))).hasErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("issuer 축")
    class IssuerAxis {

        @Test
        @DisplayName("SAS 발급자 → 통과")
        void sasIssuerPasses() {
            assertThat(gate.validate(token(SAS_ISSUER, Map.of("tenant_id", TENANT))).hasErrors())
                    .isFalse();
        }

        @Test
        @DisplayName("legacy 발급자 'iam' → 통과 (D2-b 유예 중)")
        void legacyIssuerPasses() {
            assertThat(gate.validate(token(LEGACY_ISSUER, Map.of("tenant_id", TENANT))).hasErrors())
                    .isFalse();
        }

        @Test
        @DisplayName("허용되지 않은 발급자 → invalid_issuer")
        void unknownIssuerRejected() {
            assertThat(errorCodes(gate.validate(
                    token("https://evil.example.com", Map.of("tenant_id", TENANT)))))
                    .contains("invalid_issuer");
        }

        @Test
        @DisplayName("allowed-issuers 가 비면 기동이 실패한다 — 게이트가 열린 채 뜨지 않는다")
        void emptyAllowlistFailsAtStartup() {
            assertThatThrownBy(() -> wiredConfig("").jwtTokenValidator())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * {@code SecurityConfig}'s {@code AuthenticationEntryPoint} raises 403
     * {@code TENANT_FORBIDDEN} by matching on this code. If the shared class ever changed it,
     * cross-tenant rejections would silently degrade from 403 to 401.
     */
    @Test
    @DisplayName("에러코드는 계약이다 — tenant_mismatch (403 TENANT_FORBIDDEN 이 여기에 걸려 있다)")
    void theErrorCodeIsAContract() {
        assertThat(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH).isEqualTo("tenant_mismatch");
    }
}
