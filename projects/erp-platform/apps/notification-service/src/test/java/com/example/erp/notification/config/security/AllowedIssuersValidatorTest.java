package com.example.erp.notification.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedIssuersValidatorTest {

    private final AllowedIssuersValidator validator =
            new AllowedIssuersValidator(List.of("http://gap.local", "global-account-platform"));

    private static Jwt jwt(String iss) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("iss", iss, "sub", "u"));
    }

    @Test
    void allowedIssuerPasses() {
        assertThat(validator.validate(jwt("http://gap.local")).hasErrors()).isFalse();
    }

    @Test
    void unknownIssuerFails() {
        assertThat(validator.validate(jwt("http://evil")).hasErrors()).isTrue();
    }
}
