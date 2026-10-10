package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.OperatorEligibilityUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OperatorConsoleEligibilityPort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-615 — the token of a consumer-pool principal (multi-tenancy.md § 소비자 계정 풀 § 4,
 * jwt-standard-claims.md {@code sub} / {@code tenant_id} / § Role Strategy):
 * {@code sub} = pool account, {@code tenant_id} = the requesting client's site, {@code roles} = that
 * site's seed ∪ that site's roles — and NO token without an ACTIVE membership of the site (fail-closed).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantClaimTokenCustomizer — 풀 principal 의 토큰 (TASK-BE-615)")
class TenantClaimPoolPrincipalTest {

    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-0000000c0615";

    @Mock private JwtEncodingContext context;
    @Mock private Authentication principal;
    @Mock private AccountServicePort accountServicePort;
    @Mock private OperatorConsoleEligibilityPort consoleEligibilityPort;

    private TenantClaimTokenCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer(accountServicePort, consoleEligibilityPort);
    }

    private static RegisteredClient client(String clientId, String tenant, String type) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:3000/callback")
                .clientSettings(ClientSettings.builder()
                        .setting(OAuthClientMapper.SETTING_TENANT_ID, tenant)
                        .setting(OAuthClientMapper.SETTING_TENANT_TYPE, type)
                        .build())
                .build();
    }

    private static RegisteredClient store() {
        return client("ecommerce-web-store-client", "ecommerce", "B2C");
    }

    private static RegisteredClient fan() {
        return client("demo-spa-client", "fan-platform", "B2C_CONSUMER");
    }

    private static ConsumerSiteMembershipLookupResult member(String site, String... roles) {
        return new ConsumerSiteMembershipLookupResult(site, true, "B2C_CONSUMER", "ACTIVE", List.of(roles));
    }

    /** The principal CredentialAuthenticationProvider builds for a pool credential. */
    private JwtClaimsSet.Builder poolSessionOn(RegisteredClient client, AuthorizationGrantType grant,
                                               OAuth2TokenType tokenType) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("pool@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900));
        when(principal.getDetails()).thenReturn(Map.of(
                "tenant_id", "consumer-pool",
                "tenant_type", "B2C_CONSUMER",
                "account_id", POOL_ACCOUNT,
                "email", "pool@example.com"));
        when(context.getTokenType()).thenReturn(tokenType);
        when(context.getAuthorizationGrantType()).thenReturn(grant);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claims);
        lenient().when(context.getAuthorizedScopes()).thenReturn(Set.of("openid"));
        return claims;
    }

    private static void assertInvalidGrant(Throwable t) {
        assertThat(t).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(((OAuth2AuthenticationException) t).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
    }

    // ── AC-1 — tenant = the site, sub = the pool account, roles = that site's only ─────────────

    @Test
    @DisplayName("AC-1: 스토어 client + 스토어 멤버 → tenant_id=ecommerce · sub=풀 계정 · roles=[CUSTOMER] (FAN 없음)")
    void storeClient_member_mintsStoreToken() {
        JwtClaimsSet.Builder claims = poolSessionOn(store(), AuthorizationGrantType.AUTHORIZATION_CODE,
                OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT)).thenReturn(member("ecommerce"));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("ecommerce").isNotEqualTo("consumer-pool");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C_CONSUMER");
        assertThat(built.getSubject()).isEqualTo(POOL_ACCOUNT);
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("CUSTOMER");
        // The stored-roles leg is the per-site path; the pool path never asks it.
        verify(accountServicePort, never()).listAccountRoles(anyString(), anyString());
    }

    @Test
    @DisplayName("AC-1: 같은 principal · 팬 client → tenant_id=fan-platform · 같은 sub · roles=[FAN] (CUSTOMER 없음)")
    void fanClient_member_mintsFanToken_sameSub() {
        JwtClaimsSet.Builder claims = poolSessionOn(fan(), AuthorizationGrantType.AUTHORIZATION_CODE,
                OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT))
                .thenReturn(member("fan-platform"));
        when(accountServicePort.listEntitledDomains("fan-platform")).thenReturn(List.of());

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat(built.getSubject()).isEqualTo(POOL_ACCOUNT);
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("FAN").doesNotContain("CUSTOMER");
    }

    @Test
    @DisplayName("역할 = 시드 ∪ 그 사이트 역할 (시드 먼저) — 스토어 SELLER 는 스토어 토큰에만")
    void siteRoles_unionedWithSeed_onlyThatSite() {
        JwtClaimsSet.Builder claims = poolSessionOn(store(), AuthorizationGrantType.AUTHORIZATION_CODE,
                OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT))
                .thenReturn(member("ecommerce", "SELLER", "CUSTOMER"));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());

        customizer.customize(context);

        assertThat(claims.build().<List<String>>getClaim("roles")).containsExactly("CUSTOMER", "SELLER");
    }

    @Test
    @DisplayName("id_token 도 사이트 테넌트를 싣는다 — consumer-pool 은 어디에도 없다")
    void idToken_carriesSiteTenant() {
        JwtClaimsSet.Builder claims = poolSessionOn(store(), AuthorizationGrantType.AUTHORIZATION_CODE,
                new OAuth2TokenType("id_token"));
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT)).thenReturn(member("ecommerce"));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());

        customizer.customize(context);

        assertThat((String) claims.build().getClaim("tenant_id")).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("refresh_token 도 같은 규칙 — 매번 멤버십을 다시 묻는다")
    void refresh_member_mintsSiteToken() {
        JwtClaimsSet.Builder claims = poolSessionOn(store(), AuthorizationGrantType.REFRESH_TOKEN,
                OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT)).thenReturn(member("ecommerce"));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());

        customizer.customize(context);

        assertThat((String) claims.build().getClaim("tenant_id")).isEqualTo("ecommerce");
    }

    // ── no membership → no token (and fail-closed) ─────────────────────────────────────────────

    @Test
    @DisplayName("멤버십 없음 → invalid_grant — 토큰 없음 (동의 화면은 TASK-BE-616)")
    void noMembership_noToken() {
        poolSessionOn(fan(), AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("fan-platform", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("fan-platform", true, "B2C_CONSUMER", null, List.of()));

        assertInvalidGrant(catchThrowable(() -> customizer.customize(context)));
        verify(accountServicePort, never()).listEntitledDomains(any());
    }

    @Test
    @DisplayName("LEFT 멤버십 → invalid_grant (refresh 에서도 — 탈퇴 뒤 갱신이 멈춘다)")
    void leftMembership_refresh_noToken() {
        poolSessionOn(store(), AuthorizationGrantType.REFRESH_TOKEN, OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("ecommerce", true, "B2C_CONSUMER", "LEFT", List.of()));

        assertInvalidGrant(catchThrowable(() -> customizer.customize(context)));
    }

    @Test
    @DisplayName("멤버십 조회 실패 → invalid_grant (fail-closed — 저장 역할의 fail-soft 와 다르다)")
    void lookupFailure_failsClosed() {
        poolSessionOn(store(), AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertInvalidGrant(catchThrowable(() -> customizer.customize(context)));
        verify(accountServicePort, never()).listAccountRoles(anyString(), anyString());
    }

    @Test
    @DisplayName("B2B client(wms) — 소비자 사이트가 아님 → invalid_grant")
    void nonConsumerSiteClient_noToken() {
        poolSessionOn(client("wms-web", "wms", "B2B"), AuthorizationGrantType.AUTHORIZATION_CODE,
                OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("wms", POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult("wms", false, "B2B_ENTERPRISE", null, List.of()));

        assertInvalidGrant(catchThrowable(() -> customizer.customize(context)));
    }

    // ── console — TASK-MONO-772 S4 (ADR-MONO-080 D6): a console token ONLY with a live operator facet ─────

    /** The TASK-BE-614 refusal text — byte-for-byte; the console's sso_wrong_account reads its 'consumer-pool'. */
    private static final String NO_FACET_TEXT =
            "tenant_id 'consumer-pool' is a reserved storage value and is never issued";

    private static RegisteredClient console() {
        return client("platform-console-web", "iam", "B2B_ENTERPRISE");
    }

    private static String description(Throwable t) {
        return ((OAuth2AuthenticationException) t).getError().getDescription();
    }

    @Test
    @DisplayName("🔴 AC-2: 운영자 측면 없는 풀 계정 → 콘솔 토큰 없음 · invalid_grant · 문구는 BE-614 그대로(바이트 불변) · 사이트 멤버십·역할 조회 없음")
    void console_noFacet_noToken_byteUnchangedRefusal() {
        poolSessionOn(console(), AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);
        when(consoleEligibilityPort.isConsoleEligible(POOL_ACCOUNT)).thenReturn(false);

        Throwable t = catchThrowable(() -> customizer.customize(context));

        assertInvalidGrant(t);
        assertThat(description(t)).isEqualTo(NO_FACET_TEXT);
        verify(accountServicePort, never()).getConsumerSiteMembership(anyString(), anyString());
        verify(accountServicePort, never()).listAccountRoles(anyString(), anyString());
        verify(accountServicePort, never()).listEntitledDomains(any());
    }

    @Test
    @DisplayName("대조군: 살아 있는 운영자 측면 있는 풀 계정 → 콘솔 토큰 · tenant_id=iam · sub=풀 계정 · roles·entitled_domains 없음")
    void console_liveFacet_mintsConsoleToken() {
        JwtClaimsSet.Builder claims = poolSessionOn(console(), AuthorizationGrantType.AUTHORIZATION_CODE,
                OAuth2TokenType.ACCESS_TOKEN);
        when(consoleEligibilityPort.isConsoleEligible(POOL_ACCOUNT)).thenReturn(true);

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("iam").isNotEqualTo("consumer-pool");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B_ENTERPRISE");
        assertThat(built.getSubject()).isEqualTo(POOL_ACCOUNT);
        assertThat(built.getClaims()).doesNotContainKeys("roles", "entitled_domains");
        verify(accountServicePort, never()).getConsumerSiteMembership(anyString(), anyString());
    }

    @Test
    @DisplayName("id_token 도 같다 — 측면 있으면 tenant_id=iam")
    void console_liveFacet_idToken() {
        JwtClaimsSet.Builder claims = poolSessionOn(console(), AuthorizationGrantType.AUTHORIZATION_CODE,
                new OAuth2TokenType("id_token"));
        when(consoleEligibilityPort.isConsoleEligible(POOL_ACCOUNT)).thenReturn(true);

        customizer.customize(context);

        assertThat((String) claims.build().getClaim("tenant_id")).isEqualTo("iam");
    }

    @Test
    @DisplayName("AC-3: refresh 마다 다시 묻는다 — 측면 있으면 iam, 회수(비-ACTIVE·삭제) 뒤 같은 principal 의 refresh 는 거절(BE-614 문구)")
    void console_refresh_reasksEveryTime_revokedFacetRefused() {
        JwtClaimsSet.Builder claims = poolSessionOn(console(), AuthorizationGrantType.REFRESH_TOKEN,
                OAuth2TokenType.ACCESS_TOKEN);
        when(consoleEligibilityPort.isConsoleEligible(POOL_ACCOUNT)).thenReturn(true, false);

        customizer.customize(context);
        assertThat((String) claims.build().getClaim("tenant_id")).isEqualTo("iam");

        // The operator row was suspended / removed between the two refreshes.
        Throwable t = catchThrowable(() -> customizer.customize(context));
        assertInvalidGrant(t);
        assertThat(description(t)).isEqualTo(NO_FACET_TEXT);
        verify(consoleEligibilityPort, org.mockito.Mockito.times(2)).isConsoleEligible(POOL_ACCOUNT);
    }

    @Test
    @DisplayName("AC-3 대조군: 같은 풀 계정의 스토어 토큰은 측면과 무관 — 콘솔 적격을 묻지도 않는다")
    void storeToken_unaffectedByFacet() {
        JwtClaimsSet.Builder claims = poolSessionOn(store(), AuthorizationGrantType.REFRESH_TOKEN,
                OAuth2TokenType.ACCESS_TOKEN);
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT)).thenReturn(member("ecommerce"));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());

        customizer.customize(context);

        assertThat((String) claims.build().getClaim("tenant_id")).isEqualTo("ecommerce");
        verify(consoleEligibilityPort, never()).isConsoleEligible(anyString());
    }

    @Test
    @DisplayName("판정 실패 → invalid_grant · error_description=operator_eligibility_unavailable(값 전체) · 'consumer-pool' 미포함 (fail-closed, F5)")
    void console_lookupFailure_distinctRefusal() {
        poolSessionOn(console(), AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);
        when(consoleEligibilityPort.isConsoleEligible(POOL_ACCOUNT))
                .thenThrow(new OperatorEligibilityUnavailableException("down", null));

        Throwable t = catchThrowable(() -> customizer.customize(context));

        assertInvalidGrant(t);
        assertThat(description(t)).isEqualTo("operator_eligibility_unavailable").doesNotContain("consumer-pool");
    }

    @Test
    @DisplayName("refresh 중 판정 실패도 같은 별도 거절 (토큰 회전 전에 실패 — S1-13)")
    void console_refreshLookupFailure_distinctRefusal() {
        poolSessionOn(console(), AuthorizationGrantType.REFRESH_TOKEN, OAuth2TokenType.ACCESS_TOKEN);
        when(consoleEligibilityPort.isConsoleEligible(POOL_ACCOUNT)).thenThrow(new IllegalStateException("odd"));

        Throwable t = catchThrowable(() -> customizer.customize(context));

        assertInvalidGrant(t);
        assertThat(description(t)).isEqualTo("operator_eligibility_unavailable");
    }

    // ── AC-9 control — a per-site principal never takes the pool path ─────────────────────────

    @Test
    @DisplayName("대조군(AC-9): 사이트 계정(ecommerce) principal 은 풀 경로를 타지 않는다 — 멤버십 조회 0, 저장 역할 경로 그대로")
    void sitePrincipal_untouched() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("http://localhost:8081").subject("x")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900));
        when(principal.getDetails()).thenReturn(Map.of(
                "tenant_id", "ecommerce", "tenant_type", "B2C_CONSUMER", "account_id", "acc-site"));
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(store());
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claims);
        lenient().when(context.getAuthorizedScopes()).thenReturn(Set.of("openid"));
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("ecommerce", "acc-site")).thenReturn(List.of());

        customizer.customize(context);

        assertThat(claims.build().<List<String>>getClaim("roles")).containsExactly("CUSTOMER");
        verify(accountServicePort, never()).getConsumerSiteMembership(anyString(), anyString());
    }
}
