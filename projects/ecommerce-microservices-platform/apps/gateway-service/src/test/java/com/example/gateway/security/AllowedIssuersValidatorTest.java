package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AllowedIssuersValidator 단위 테스트 (gateway)")
class AllowedIssuersValidatorTest {

    private final AllowedIssuersValidator validator = new AllowedIssuersValidator(
            List.of("http://localhost:8081", "global-account-platform"));

    private static Jwt jwt(String issuer) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenant_id", "ecommerce")
                .build();
    }

    @Test
    @DisplayName("SAS issuer (configured URL) → success")
    void sasIssuerPasses() {
        assertThat(validator.validate(jwt("http://localhost:8081")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("legacy 'global-account-platform' issuer → success")
    void legacyIssuerPasses() {
        assertThat(validator.validate(jwt("global-account-platform")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("unknown issuer → invalid_issuer")
    void unknownIssuerRejected() {
        OAuth2TokenValidatorResult r = validator.validate(jwt("https://attacker.example.com"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("empty allowlist → IllegalArgumentException")
    void emptyAllowlistRejected() {
        assertThatThrownBy(() -> new AllowedIssuersValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
