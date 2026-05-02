package com.example.community.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AllowedIssuersValidator 단위 테스트")
class AllowedIssuersValidatorTest {

    private final AllowedIssuersValidator validator = new AllowedIssuersValidator(
            List.of("http://localhost:8081", "global-account-platform"));

    private static Jwt jwt(String issuer) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("acc-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenant_id", "fan-platform")
                .build();
    }

    @Test
    @DisplayName("SAS 발급 issuer → success")
    void sasIssuerPasses() {
        assertThat(validator.validate(jwt("http://localhost:8081")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("legacy issuer → success")
    void legacyIssuerPasses() {
        assertThat(validator.validate(jwt("global-account-platform")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("unknown issuer → invalid_issuer error")
    void unknownIssuerRejected() {
        OAuth2TokenValidatorResult r = validator.validate(jwt("https://attacker.example.com"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("빈 allowedIssuers 리스트 → IllegalArgumentException")
    void emptyAllowlistRejected() {
        assertThatThrownBy(() -> new AllowedIssuersValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
