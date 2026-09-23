package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D) § D4 — the assumed
 * <b>workload</b> token must not acquire the trappings of an identity.
 *
 * <p>🔴 <b>This class is mostly absence assertions, and that is deliberate.</b> The failure
 * ADR-MONO-076 names as most expensive is not "the feature does not work" — it is "a workload
 * token that looks like an operator token". Absence is what has to be pinned, because the
 * operator branch has grown a new derivation on nearly every ticket that touched it
 * (org_scope, role derivation, delegated scope, {@code sub} alignment) and the next one would
 * arrive silently.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantClaimTokenCustomizer 워크로드 assume-tenant 분기 (TASK-MONO-721 / D4)")
class WorkloadAssumeTenantCustomizerTest {

    private static final String CLIENT_ID = "product-service-client";
    private static final String TARGET_TENANT = "ecommerce";

    private TenantClaimTokenCustomizer customizer;

    @Mock
    private JwtEncodingContext context;

    @Mock
    private AccountServicePort accountServicePort;

    @Mock
    private Authentication clientPrincipal;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer(accountServicePort);
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject(CLIENT_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));
    }

    private WorkloadAssumeTenantAuthenticationToken grant(String tenantId, String tenantType) {
        return new WorkloadAssumeTenantAuthenticationToken(
                clientPrincipal, CLIENT_ID, tenantId, tenantType);
    }

    @Test
    @DisplayName("tenant_id 는 **대상 테넌트**가 된다 — 이것이 이 교환의 전부다")
    void injectsTheTargetTenant() {
        JwtClaimsSet.Builder builder = claims();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(grant(TARGET_TENANT, "B2B_ENTERPRISE"));
        when(context.getClaims()).thenReturn(builder);

        customizer.customize(context);

        JwtClaimsSet built = builder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo(TARGET_TENANT);
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B_ENTERPRISE");
    }

    @Test
    @DisplayName("🔴 D4 — 운영자 파생이 **하나도** 실리지 않는다 (org_scope · entitled_domains · roles)")
    void carriesNoneOfTheOperatorDerivations() {
        JwtClaimsSet.Builder builder = claims();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(grant(TARGET_TENANT, "B2B_ENTERPRISE"));
        when(context.getClaims()).thenReturn(builder);

        customizer.customize(context);

        JwtClaimsSet built = builder.build();

        // 🔴 org_scope: the operator branch injects ["*"] when the assignment carries none —
        // a net-zero default there, a silent grant of the WHOLE TENANT here.
        assertThat((Object) built.getClaim("org_scope")).as("org_scope must be absent").isNull();

        // 🔴 entitled_domains + roles: deriving a workload's authority from the tenant it is
        // acting on would let the TARGET decide the caller's power, inverting ADR-MONO-061's
        // per-client enumeration.
        assertThat((Object) built.getClaim("entitled_domains")).as("entitled_domains must be absent").isNull();
        assertThat((Object) built.getClaim("roles")).as("derived roles must be absent").isNull();

        // A workload is not an identity (jwt-standard-claims.md § the email row).
        assertThat((Object) built.getClaim("email")).as("email must be absent").isNull();
    }

    @Test
    @DisplayName("🔴 D4 — `sub` 는 **클라이언트 그대로**다 (게이트웨이 여섯이 X-User-Id 를 여기서 읽는다)")
    void subStaysTheClient() {
        JwtClaimsSet.Builder builder = claims();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(grant(TARGET_TENANT, "B2B_ENTERPRISE"));
        when(context.getClaims()).thenReturn(builder);

        customizer.customize(context);

        assertThat(builder.build().getSubject()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("🔵 account-service 를 부르지 않는다 — 워크로드에는 물어볼 계정이 없다")
    void doesNotCallAccountService() {
        JwtClaimsSet.Builder builder = claims();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(grant(TARGET_TENANT, "B2B_ENTERPRISE"));
        when(context.getClaims()).thenReturn(builder);

        customizer.customize(context);

        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("fail-closed — 대상 테넌트가 비면 토큰을 만들지 않고 터진다")
    void blankTenantFailsClosed() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(grant("   ", "B2B_ENTERPRISE"));

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class);
    }
}
