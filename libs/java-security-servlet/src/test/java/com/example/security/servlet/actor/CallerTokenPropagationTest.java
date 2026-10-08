package com.example.security.servlet.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * The promoted caller-token read + forward (TASK-MONO-778). {@code currentCallerToken()} covers
 * AC-1 (no authentication / a non-JWT principal -> {@code null}; a {@link JwtAuthenticationToken}
 * subtype such as {@link ActorAuthenticationToken} -> its token). {@code withBearerToken} covers
 * AC-3 — the forwarded header must be byte-identical to the original token value, so every claim
 * (e.g. {@code amr}) survives unchanged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CallerTokenPropagation — read the caller's JWT, forward it as Bearer, mechanism only")
class CallerTokenPropagationTest {

    @Mock
    RestClient.RequestHeadersSpec<?> spec;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(String tokenValue) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("acc-1")
                .claim("tenant_id", "tenant-x")
                .claim("amr", List.of("otp"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    // ------------------------------------------------------------------
    // AC-1 — currentCallerToken()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no authentication -> null")
    void noAuthenticationReturnsNull() {
        SecurityContextHolder.clearContext();

        assertThat(CallerTokenPropagation.currentCallerToken()).isNull();
    }

    @Test
    @DisplayName("a non-JWT Authentication -> null")
    void nonJwtAuthenticationReturnsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("acc-1", "n/a"));

        assertThat(CallerTokenPropagation.currentCallerToken()).isNull();
    }

    @Test
    @DisplayName("a JwtAuthenticationToken subtype (a service's own actor principal) -> its token")
    void actorAuthenticationTokenReturnsItsToken() {
        Jwt token = jwt("raw-token-value");
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(token, new Object(), "acc-1", List.of()));

        assertThat(CallerTokenPropagation.currentCallerToken()).isSameAs(token);
    }

    @Test
    @DisplayName("a plain JwtAuthenticationToken -> its token")
    void plainJwtAuthenticationTokenReturnsItsToken() {
        Jwt token = jwt("raw-token-value");
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of(), "client-x"));

        assertThat(CallerTokenPropagation.currentCallerToken()).isSameAs(token);
    }

    // ------------------------------------------------------------------
    // AC-3 — withBearerToken(): byte-identical forwarding, no claim rebuilding
    // ------------------------------------------------------------------

    @Test
    @DisplayName("forwards the raw token value byte-identical, surviving claims this class never reads (amr)")
    void withBearerTokenForwardsRawTokenValueByteIdentical() {
        String rawTokenValue = "header.payload-with-amr-otp-claim.signature";
        Jwt token = jwt(rawTokenValue);
        // doReturn/when (not when/thenReturn): the wildcard RequestHeadersSpec<?> re-captures a
        // fresh type variable at every reference, so `when(spec.header(...)).thenReturn(spec)`
        // does not typecheck — doReturn's untyped Object parameter sidesteps that.
        doReturn(spec).when(spec).header(anyString(), anyString());

        RestClient.RequestHeadersSpec<?> result = CallerTokenPropagation.withBearerToken(spec, token);

        assertThat(result).isSameAs(spec);
        verify(spec).header(HttpHeaders.AUTHORIZATION, "Bearer " + rawTokenValue);
        // Not rebuilt: the claims the forwarding helper never looks at are still on the token object
        // whose getTokenValue() just went out on the wire.
        assertThat(token.getClaimAsStringList("amr")).containsExactly("otp");
    }
}
