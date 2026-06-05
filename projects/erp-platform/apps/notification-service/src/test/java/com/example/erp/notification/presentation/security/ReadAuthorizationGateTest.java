package com.example.erp.notification.presentation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * READ authorization gate (E6, fail-closed): READ = erp.read ∨ operator ∨
 * entitled.
 */
class ReadAuthorizationGateTest {

    private final ReadAuthorizationGate gate = new ReadAuthorizationGate("erp");

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    void erpReadScopeAllowed() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("scope", "erp.read", "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void operatorRoleAllowed() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("roles", List.of("ERP_OPERATOR"), "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void entitledAllowed() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("entitled_domains", List.of("erp"), "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void noScopeRoleOrEntitlementDenied() {
        assertThatThrownBy(() -> gate.requireRead(jwt(Map.of("sub", "u"))))
                .isInstanceOf(ReadAccessDeniedException.class);
    }

    @Test
    void nullJwtDenied() {
        assertThatThrownBy(() -> gate.requireRead(null))
                .isInstanceOf(ReadAccessDeniedException.class);
    }
}
