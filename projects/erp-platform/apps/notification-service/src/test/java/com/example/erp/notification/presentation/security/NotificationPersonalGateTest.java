package com.example.erp.notification.presentation.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions Q2 · Q6, 2026-10-15 UTC) — the inbox is a PERSONAL endpoint:
 * any ERP business role (or the derived entry role) or an erp scope opens it; the subscription alone does not.
 * Recipient scoping ({@code sub}) is enforced separately by the use cases and is unchanged.
 */
class NotificationPersonalGateTest {

    private final ReadAuthorizationGate gate = new ReadAuthorizationGate("erp");

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    private static Map<String, Object> operator(List<String> roles) {
        return Map.of("sub", "u", "tenant_id", "demo-corp", "roles", roles, "entitled_domains", List.of("erp"));
    }

    @Test
    @DisplayName("🔴 793 Q2: a masterdata business role alone (no entry role on the wire) → inbox opens")
    void masterdataRole_opens() {
        assertThatCode(() -> gate.requireRead(jwt(operator(List.of("ERP_MASTERDATA_READ")))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("🔴 793 Q2: an approval business role alone → inbox opens")
    void approvalRole_opens() {
        assertThatCode(() -> gate.requireRead(jwt(operator(List.of("ERP_APPROVAL_WRITE")))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("793 Q2: the derived entry role → inbox opens")
    void entryRole_opens() {
        assertThatCode(() -> gate.requireRead(jwt(operator(List.of("ERP_OPERATOR")))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("🔴 793 Q1: subscription only (entitled_domains ∋ erp, no role) → 403")
    void subscriptionOnly_refused() {
        assertThatThrownBy(() -> gate.requireRead(jwt(Map.of("sub", "u", "tenant_id", "demo-corp",
                "entitled_domains", List.of("erp")))))
                .isInstanceOf(ReadAccessDeniedException.class);
    }

    @Test
    @DisplayName("793 control: a role of another domain only (WMS set) → 403")
    void otherDomainRole_refused() {
        assertThatThrownBy(() -> gate.requireRead(jwt(operator(List.of("OUTBOUND_READ", "WMS_OPERATOR")))))
                .isInstanceOf(ReadAccessDeniedException.class);
    }

    @Test
    @DisplayName("793 control: erp.read scope and tenant_id='*' keep opening it")
    void scopeAndWildcard_unchanged() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("sub", "u", "scope", "erp.read"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> gate.requireRead(jwt(Map.of("sub", "u", "tenant_id", "*"))))
                .doesNotThrowAnyException();
    }
}
