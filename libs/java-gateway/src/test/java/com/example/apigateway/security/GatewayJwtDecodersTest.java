package com.example.apigateway.security;

import com.example.security.oauth2.AudienceMode;
import com.example.security.oauth2.AllowedAudiencesValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * The validator chain — the part of each gateway's {@code OAuth2ResourceServerConfig} that
 * was identical three times over, and the part where duplication is most expensive: three
 * hand-maintained copies of "which checks run, and in what order" is exactly the shape that
 * lost the {@code FailOpenRateLimiter} fix (ADR-MONO-048 § 1.3).
 */
@DisplayName("GatewayJwtDecoders — 검증 체인 조립")
class GatewayJwtDecodersTest {

    private static final OAuth2TokenValidator<Jwt> ANY_TENANT =
            TenantClaimValidator.forTenant("wms").build();
    private static final String ALLOWED_CLIENT = "allowed-client";

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    private AllowedAudiencesValidator audience(AudienceMode mode) {
        return new AllowedAudiencesValidator("test-gateway", List.of(ALLOWED_CLIENT), mode, registry);
    }

    @Test
    @DisplayName("체인 구성: 만료 → issuer 허용목록 → 도메인 테넌트 게이트 → 스프링 기본값, 그 뒤에 audience 게이트")
    void chainContainsTimestampIssuerTenantGateThenAudience() throws Exception {
        AllowedAudiencesValidator audienceGate = audience(AudienceMode.SHADOW);
        OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                List.of("http://iam.local"), audienceGate, ANY_TENANT);

        assertThat(chain).isInstanceOf(GatewayJwtDecoders.AudienceCheckedChain.class);
        GatewayJwtDecoders.AudienceCheckedChain sequenced = (GatewayJwtDecoders.AudienceCheckedChain) chain;
        assertThat(sequenced.audienceGate()).isSameAs(audienceGate);

        List<?> delegates = delegatesOf(sequenced.base());
        assertThat(delegates).element(0).isInstanceOf(JwtTimestampValidator.class);
        assertThat(delegates).element(1).isInstanceOf(AllowedIssuersValidator.class);
        assertThat(delegates).element(2).isSameAs(ANY_TENANT);
        assertThat(delegates).hasSize(4);
    }

    /**
     * The issuer allowlist may not silently degrade to "accept any issuer" — an empty list is
     * a misconfiguration, and {@link AllowedIssuersValidator} refuses to be constructed from
     * one. That refusal is what keeps a missing {@code allowed-issuers} property from turning
     * into an open edge, so it is asserted here rather than assumed.
     */
    @Test
    @DisplayName("빈 issuer 허용목록은 '아무 issuer 나 허용' 으로 강등되지 않고 조립 자체가 실패한다")
    void emptyIssuerListIsRejectedRatherThanTreatedAsAllowAll() {
        AllowedAudiencesValidator audienceGate = audience(AudienceMode.SHADOW);
        assertThatThrownBy(() -> GatewayJwtDecoders.validatorChain(List.of(), audienceGate, ANY_TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedIssuers");
    }

    @Test
    @DisplayName("audience 게이트는 생략할 수 없다 — null 이면 조립이 실패한다")
    void audienceGateIsRequired() {
        assertThatThrownBy(() -> GatewayJwtDecoders.validatorChain(
                List.of("http://iam.local"), null, ANY_TENANT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("audienceGate");
    }

    @Test
    @DisplayName("설정되지 않은 issuer 는 체인에서 거부된다")
    void chainRejectsUnknownIssuer() {
        OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                List.of("http://iam.local"), audience(AudienceMode.SHADOW), ANY_TENANT);

        OAuth2TokenValidatorResult r = chain.validate(jwt("https://attacker.example", "wms", ALLOWED_CLIENT));

        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("교차 테넌트 토큰은 체인에서 tenant_mismatch 로 거부된다")
    void chainRejectsCrossTenant() {
        OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                List.of("http://iam.local"), audience(AudienceMode.SHADOW), ANY_TENANT);

        OAuth2TokenValidatorResult r = chain.validate(jwt("http://iam.local", "scm", ALLOWED_CLIENT));

        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> GatewayErrorCodes.TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    void chainAcceptsAValidToken() {
        OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                List.of("http://iam.local", "iam"), audience(AudienceMode.ENFORCE), ANY_TENANT);

        assertThat(chain.validate(jwt("http://iam.local", "wms", ALLOWED_CLIENT)).hasErrors()).isFalse();
        assertThat(chain.validate(jwt("iam", "wms", ALLOWED_CLIENT)).hasErrors())
                .as("legacy 'iam' issuer during the D2-b deprecation window")
                .isFalse();
    }

    @Test
    @DisplayName("parseCsv — 트림하고 빈 항목은 버린다; null 은 빈 목록")
    void parseCsvTrimsAndDropsEmpties() {
        assertThat(GatewayJwtDecoders.parseCsv(" http://iam.local , iam ,, "))
                .containsExactly("http://iam.local", "iam");
        assertThat(GatewayJwtDecoders.parseCsv(null)).isEmpty();
        assertThat(GatewayJwtDecoders.parseCsv("")).isEmpty();
    }

    /**
     * TASK-MONO-696 — the audience gate runs only on a token the rest of the chain accepted.
     * Were it a side-by-side delegate, a refused token would also be measured (inflating the
     * shadow mismatch count), and an enforce-mode issuer failure would carry an audience error
     * the entry point maps to 403 ahead of the 401 it owes.
     */
    @Nested
    @DisplayName("TASK-MONO-696 — audience 게이트는 나머지 체인을 통과한 토큰에만 적용된다")
    class AudienceSequencing {

        @Test
        @DisplayName("ENFORCE: 허용 목록 밖 aud 만 틀린 토큰 → audience_mismatch 하나만")
        void enforce_foreignAudienceOnly_carriesOnlyAudienceError() {
            OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                    List.of("http://iam.local"), audience(AudienceMode.ENFORCE), ANY_TENANT);

            OAuth2TokenValidatorResult r = chain.validate(jwt("http://iam.local", "wms", "other-client"));

            assertThat(r.getErrors()).extracting(e -> e.getErrorCode())
                    .containsExactly(GatewayErrorCodes.AUDIENCE_MISMATCH);
        }

        @Test
        @DisplayName("ENFORCE: issuer 도 aud 도 틀린 토큰 → issuer 오류만 (audience 는 평가되지 않음)")
        void enforce_wrongIssuerAndForeignAudience_carriesNoAudienceError() {
            OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                    List.of("http://iam.local"), audience(AudienceMode.ENFORCE), ANY_TENANT);

            OAuth2TokenValidatorResult r = chain.validate(jwt("https://attacker.example", "wms", "other-client"));

            assertThat(r.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
            assertThat(r.getErrors()).noneMatch(
                    e -> GatewayErrorCodes.AUDIENCE_MISMATCH.equals(e.getErrorCode()));
            assertThat(outcome(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED)).isZero();
        }

        @Test
        @DisplayName("SHADOW: 테넌트로 이미 거절된 토큰은 audience 불일치로 세지 않는다")
        void shadow_refusedToken_isNotCountedAsAudienceMismatch() {
            OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                    List.of("http://iam.local"), audience(AudienceMode.SHADOW), ANY_TENANT);

            chain.validate(jwt("http://iam.local", "scm", "other-client"));

            assertThat(outcome(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED)).isZero();
            assertThat(outcome(AllowedAudiencesValidator.OUTCOME_MATCH)).isZero();
        }

        @Test
        @DisplayName("SHADOW: 유효한 토큰의 aud 불일치 → 통과 + mismatch_shadowed 1")
        void shadow_validTokenForeignAudience_passesAndIsCounted() {
            OAuth2TokenValidator<Jwt> chain = GatewayJwtDecoders.validatorChain(
                    List.of("http://iam.local"), audience(AudienceMode.SHADOW), ANY_TENANT);

            OAuth2TokenValidatorResult r = chain.validate(jwt("http://iam.local", "wms", "other-client"));

            assertThat(r.hasErrors()).isFalse();
            assertThat(outcome(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED)).isEqualTo(1.0);
        }
    }

    private double outcome(String outcome) {
        var counter = registry.find(AllowedAudiencesValidator.METRIC_NAME)
                .tag(AllowedAudiencesValidator.TAG_GATEWAY, "test-gateway")
                .tag(AllowedAudiencesValidator.TAG_OUTCOME, outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static Jwt jwt(String issuer, String tenantId, String audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("user-1")
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> delegatesOf(OAuth2TokenValidator<Jwt> chain) throws Exception {
        Field f = DelegatingOAuth2TokenValidator.class.getDeclaredField("tokenValidators");
        f.setAccessible(true);
        return (List<Object>) f.get(chain);
    }
}
