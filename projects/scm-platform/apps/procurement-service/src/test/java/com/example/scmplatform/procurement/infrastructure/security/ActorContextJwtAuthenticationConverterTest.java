package com.example.scmplatform.procurement.infrastructure.security;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.security.servlet.actor.ActorContextJwtAuthenticationConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-SCM-BE-050 — the JWT→{@link ActorContext} converter lifts the raw {@code sub}
 * with no length cap. For scm's client-credentials caller {@code sub == client_id}
 * ({@code scm-platform-internal-services-client}, 37 chars — iam-integration.md
 * Edge Case E1). This proves the converter surfaces that full id (no roles → maps
 * to {@link ActorType#BUYER}); the downstream widened columns then store it.
 *
 * <p>TASK-SCM-BE-054 (ADR-MONO-058 § D1): the converter is now the shared
 * {@link ActorContextJwtAuthenticationConverter}, parameterised with procurement's own
 * {@code ActorContext::new}. The assertions are unchanged — the point of keeping them is that
 * they are about the <em>resolution path this service actually runs</em>, and a shared mechanism
 * that dropped the full {@code sub} would fail here exactly as a local one would.
 */
class ActorContextJwtAuthenticationConverterTest {

    private static final String CLIENT_CREDENTIALS_SUB = "scm-platform-internal-services-client";

    private final ActorContextJwtAuthenticationConverter<ActorContext> converter =
            new ActorContextJwtAuthenticationConverter<>(ActorContext::new);

    private static Jwt.Builder baseJwt(String sub) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("tenant_id", "scm");
    }

    @Test
    @DisplayName("client-credentials sub (37 chars) is lifted verbatim into ActorContext.accountId()")
    void liftsFullClientCredentialsSub() {
        assertThat(CLIENT_CREDENTIALS_SUB).hasSize(37);
        Jwt jwt = baseJwt(CLIENT_CREDENTIALS_SUB).build();

        AbstractAuthenticationToken token = converter.convert(jwt);
        ActorContext actor = (ActorContext) token.getPrincipal();

        assertThat(actor.accountId()).isEqualTo(CLIENT_CREDENTIALS_SUB);
        assertThat(actor.tenantId()).isEqualTo("scm");
        // No roles claim on a client-credentials token → maps to BUYER (not operator).
        assertThat(actor.actorType()).isEqualTo(ActorType.BUYER);
    }

    @Test
    @DisplayName("human-operator UUID sub still round-trips unchanged (no regression)")
    void liftsOperatorUuidSub() {
        String uuidSub = "0192cccc-0000-0000-0000-000000000001";
        Jwt jwt = baseJwt(uuidSub).claim("roles", java.util.List.of("OPERATOR")).build();

        AbstractAuthenticationToken token = converter.convert(jwt);
        ActorContext actor = (ActorContext) token.getPrincipal();

        assertThat(actor.accountId()).isEqualTo(uuidSub);
        assertThat(actor.actorType()).isEqualTo(ActorType.OPERATOR);
    }
}
