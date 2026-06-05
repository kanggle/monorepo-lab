package com.wms.inventory.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedIssuersValidatorTest {

    private final AllowedIssuersValidator validator = new AllowedIssuersValidator(
            List.of("http://localhost:8081", "iam"));

    private static Jwt jwt(String issuer) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("operator-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenant_id", "wms")
                .build();
    }

    @Test
    void sasIssuerPasses() {
        assertThat(validator.validate(jwt("http://localhost:8081")).hasErrors()).isFalse();
    }

    @Test
    void legacyIssuerPasses() {
        assertThat(validator.validate(jwt("iam")).hasErrors()).isFalse();
    }

    @Test
    void unknownIssuerRejected() {
        OAuth2TokenValidatorResult r = validator.validate(jwt("https://attacker.example.com"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    void emptyAllowlistRejected() {
        assertThatThrownBy(() -> new AllowedIssuersValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
