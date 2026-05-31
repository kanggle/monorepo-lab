package com.example.erp.masterdata.infrastructure.security;

import com.example.erp.masterdata.application.ActorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code entitled_domains} entitlement-trust claim (ADR-MONO-019
 * § D5 / TASK-MONO-161) is threaded into the {@link ActorContext} fail-closed,
 * mirroring {@code TenantClaimValidator.safeStringList} shape-handling.
 */
class ActorContextJwtAuthenticationConverterTest {

    private final ActorContextJwtAuthenticationConverter converter =
            new ActorContextJwtAuthenticationConverter();

    private static Jwt.Builder base(Object entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .header("alg", "RS256")
                .subject("op-user-1")
                .claim("tenant_id", "globex-corp");
        if (entitledDomains != null) {
            b.claim("entitled_domains", entitledDomains);
        }
        return b;
    }

    private ActorContext actorFrom(Jwt jwt) {
        AbstractAuthenticationToken token = converter.convert(jwt);
        return (ActorContext) token.getPrincipal();
    }

    @Test
    @DisplayName("entitled_domains list of strings → threaded into ActorContext; isEntitledTo true for members")
    void listOfStringsThreaded() {
        ActorContext actor = actorFrom(base(List.of("scm", "erp")).build());
        assertThat(actor.entitledDomains()).containsExactlyInAnyOrder("scm", "erp");
        assertThat(actor.isEntitledTo("erp")).isTrue();
        assertThat(actor.isEntitledTo("scm")).isTrue();
        assertThat(actor.isEntitledTo("finance")).isFalse();
    }

    @Test
    @DisplayName("fail-closed: absent entitled_domains → empty set, isEntitledTo false")
    void absentClaimEmpty() {
        ActorContext actor = actorFrom(base(null).build());
        assertThat(actor.entitledDomains()).isEmpty();
        assertThat(actor.isEntitledTo("erp")).isFalse();
    }

    @Test
    @DisplayName("fail-closed: non-list entitled_domains (string) → empty set, isEntitledTo false")
    void nonListShapeEmpty() {
        ActorContext actor = actorFrom(base("erp,scm").build());
        assertThat(actor.entitledDomains()).isEmpty();
        assertThat(actor.isEntitledTo("erp")).isFalse();
    }

    @Test
    @DisplayName("fail-closed: list with non-string / blank elements → only valid strings kept")
    void mixedElementShapeFiltered() {
        ActorContext actor = actorFrom(base(List.of("erp", "", Map.of("k", "v"), 42)).build());
        assertThat(actor.entitledDomains()).containsExactly("erp");
        assertThat(actor.isEntitledTo("erp")).isTrue();
    }
}
