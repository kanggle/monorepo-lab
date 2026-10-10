package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-615 AC-7 — the «before» control for {@code TASK-MONO-745}, measured BEFORE this ticket
 * changes issuance and kept green after it.
 *
 * <p>A seller account today is a <b>site</b> account in {@code ecommerce} whose stored
 * {@code account_roles(ecommerce, acct)} is {@code [SELLER]} (product-service
 * {@code AccountServiceSellerProvisioner} provisions it with {@code roles=[SELLER]}). The
 * {@code authorization_code} roles leg emits stored roles <b>verbatim, never unioned with the
 * seed</b> ({@code TenantClaimTokenCustomizer#populateRoles}), so that seller's storefront token
 * carries {@code ["SELLER"]} and NOT {@code CUSTOMER} — and web-store's {@code signInCallback}
 * refuses a token without {@code CUSTOMER} ({@code account_type_mismatch}). In short: a seller
 * cannot shop with the seller account.
 *
 * <p>This ticket's pool rule (seed ∪ site roles) does NOT touch that account: sellers are not pool
 * accounts until {@code TASK-MONO-745} moves them. The test therefore stays exactly as written
 * after the pool change — if it flips, the pool branch leaked onto a site account.
 *
 * <p>Unit half (runs locally). The integration half, through the real browser form login and
 * token endpoint, is {@code SellerStoreTokenRolesBaselineIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AC-7 대조군 — 셀러(사이트 계정, 저장 역할 SELLER)의 스토어 토큰 roles = [SELLER] 뿐, CUSTOMER 없음")
class SellerStoreTokenRolesBaselineTest {

    private static final String SELLER_ACCOUNT_ID = "0199de70-0000-7000-8000-00000005e115";

    @Mock private JwtEncodingContext context;
    @Mock private Authentication principal;
    @Mock private AccountServicePort accountServicePort;

    private TenantClaimTokenCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer(accountServicePort, ConsoleEligibilityStubs.NOT_ASKED);
    }

    private static RegisteredClient storeClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("ecommerce-web-store-client")
                .clientName("ecommerce Web Store")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:3000/api/auth/callback/iam")
                .clientSettings(ClientSettings.builder()
                        .setting(OAuthClientMapper.SETTING_TENANT_ID, "ecommerce")
                        .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C")
                        .build())
                .build();
    }

    private JwtClaimsSet.Builder sellerSessionOnStore(AuthorizationGrantType grant) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("seller@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900));
        // The principal CredentialAuthenticationProvider builds for an ecommerce site credential.
        when(principal.getDetails()).thenReturn(Map.of(
                "tenant_id", "ecommerce",
                "tenant_type", "B2C_CONSUMER",
                "account_id", SELLER_ACCOUNT_ID));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("ecommerce", SELLER_ACCOUNT_ID)).thenReturn(List.of("SELLER"));
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(grant);
        when(context.getRegisteredClient()).thenReturn(storeClient());
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claims);
        return claims;
    }

    @Test
    @DisplayName("authorization_code: 저장 역할 [SELLER] 가 시드를 대체 → roles == [SELLER], CUSTOMER 없음")
    void sellerOnStore_authorizationCode_rolesAreSellerOnly() {
        JwtClaimsSet.Builder claims = sellerSessionOnStore(AuthorizationGrantType.AUTHORIZATION_CODE);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("SELLER");
        assertThat(built.<List<String>>getClaim("roles")).doesNotContain("CUSTOMER");
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("ecommerce");
        assertThat(built.getSubject()).isEqualTo(SELLER_ACCOUNT_ID);
    }

    @Test
    @DisplayName("refresh_token: 같은 규칙 — 갱신 토큰도 [SELLER] 뿐")
    void sellerOnStore_refresh_rolesAreSellerOnly() {
        JwtClaimsSet.Builder claims = sellerSessionOnStore(AuthorizationGrantType.REFRESH_TOKEN);

        customizer.customize(context);

        assertThat(claims.build().<List<String>>getClaim("roles")).containsExactly("SELLER");
    }
}
