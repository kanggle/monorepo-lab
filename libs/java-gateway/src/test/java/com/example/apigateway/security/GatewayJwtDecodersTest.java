package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("체인 구성: 만료 → issuer 허용목록 → 도메인 테넌트 게이트 → 스프링 기본값")
    void chainContainsTimestampIssuerAndTenantGateInOrder() throws Exception {
        OAuth2TokenValidator<Jwt> chain =
                GatewayJwtDecoders.validatorChain(List.of("http://iam.local"), ANY_TENANT);

        assertThat(chain).isInstanceOf(DelegatingOAuth2TokenValidator.class);
        List<?> delegates = delegatesOf(chain);

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
        assertThatThrownBy(() -> GatewayJwtDecoders.validatorChain(List.of(), ANY_TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedIssuers");
    }

    @Test
    @DisplayName("설정되지 않은 issuer 는 체인에서 거부된다")
    void chainRejectsUnknownIssuer() {
        OAuth2TokenValidator<Jwt> chain =
                GatewayJwtDecoders.validatorChain(List.of("http://iam.local"), ANY_TENANT);

        OAuth2TokenValidatorResult r = chain.validate(jwt("https://attacker.example", "wms"));

        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("교차 테넌트 토큰은 체인에서 tenant_mismatch 로 거부된다")
    void chainRejectsCrossTenant() {
        OAuth2TokenValidator<Jwt> chain =
                GatewayJwtDecoders.validatorChain(List.of("http://iam.local"), ANY_TENANT);

        OAuth2TokenValidatorResult r = chain.validate(jwt("http://iam.local", "scm"));

        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> GatewayErrorCodes.TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    void chainAcceptsAValidToken() {
        OAuth2TokenValidator<Jwt> chain =
                GatewayJwtDecoders.validatorChain(List.of("http://iam.local", "iam"), ANY_TENANT);

        assertThat(chain.validate(jwt("http://iam.local", "wms")).hasErrors()).isFalse();
        assertThat(chain.validate(jwt("iam", "wms")).hasErrors())
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

    private static Jwt jwt(String issuer, String tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("user-1")
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
