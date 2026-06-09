package com.example.fanplatform.membership.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedIssuersValidatorTest {

    private final AllowedIssuersValidator validator =
            new AllowedIssuersValidator(List.of("http://test-issuer", "iam"));

    private static Jwt jwt(String issuer) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(issuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("acc1")
                .build();
    }

    @Test
    @DisplayName("allowed issuer passes")
    void allowedIssuer() {
        assertThat(validator.validate(jwt("http://test-issuer")).hasErrors()).isFalse();
        assertThat(validator.validate(jwt("iam")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("unknown issuer fails")
    void unknownIssuer() {
        assertThat(validator.validate(jwt("http://evil")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("empty allow-list is rejected at construction")
    void emptyListRejected() {
        assertThatThrownBy(() -> new AllowedIssuersValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
