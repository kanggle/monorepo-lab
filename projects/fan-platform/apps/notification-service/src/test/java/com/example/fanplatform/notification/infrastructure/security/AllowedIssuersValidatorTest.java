package com.example.fanplatform.notification.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedIssuersValidatorTest {

    private final AllowedIssuersValidator validator =
            new AllowedIssuersValidator(List.of("http://iam.local", "iam"));

    private static Jwt jwtWithIssuer(String iss) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .issuer(iss)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    void acceptsAllowedIssuer() {
        assertThat(validator.validate(jwtWithIssuer("http://iam.local")).hasErrors()).isFalse();
    }

    @Test
    void acceptsLegacyIssuer() {
        assertThat(validator.validate(jwtWithIssuer("iam")).hasErrors()).isFalse();
    }

    @Test
    void rejectsUnknownIssuer() {
        assertThat(validator.validate(jwtWithIssuer("http://evil.example")).hasErrors()).isTrue();
    }

    @Test
    void rejectsEmptyConfiguredList() {
        assertThatThrownBy(() -> new AllowedIssuersValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
