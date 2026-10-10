package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S2b — the {@code amr} claim (RFC 8176) on every identity-bearing token
 * ({@code platform/contracts/jwt-standard-claims.md} § {@code amr}; owner decision OD-7 «항상»).
 *
 * <p>🔴 «전» 상태 (measured before the customizer change, 2026-10-08): the authorization_code / id_token /
 * refresh_token cases below were RED — a session carrying {@code details.amr} still produced a token with no
 * {@code amr} claim at all. The S2b change turns them green; the «absent stays absent» case was green before
 * and after (an authorization stored before this change must degrade, not fail).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("amr 클레임 발급 (TASK-MONO-771 S2b)")
class TenantClaimAmrTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000771";

    @Mock
    private JwtEncodingContext context;

    @Mock
    private AccountServicePort accountServicePort;

    private TenantClaimTokenCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer(accountServicePort, ConsoleEligibilityStubs.NOT_ASKED);
    }

    @Test
    @DisplayName("authorization_code access token: 폼 로그인 세션 → amr=[\"pwd\"]")
    void authorizationCode_password_mintsPwd() {
        JwtClaimsSet built = mint(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.AUTHORIZATION_CODE,
                session(list("pwd")));

        assertThat(built.<List<String>>getClaim("amr")).containsExactly("pwd");
    }

    @Test
    @DisplayName("id_token 도 같은 값을 싣는다 — pwd+otp+mfa")
    void idToken_carriesSameAmr() {
        JwtClaimsSet built = mint(new OAuth2TokenType("id_token"), AuthorizationGrantType.AUTHORIZATION_CODE,
                session(list("pwd", "otp", "mfa")));

        assertThat(built.<List<String>>getClaim("amr")).containsExactly("pwd", "otp", "mfa");
    }

    @Test
    @DisplayName("🔴 refresh_token: 저장된 인가의 principal(로그인 시점 값)을 그대로 — 2단계를 거친 세션의 mfa 가 유지된다")
    void refresh_keepsOriginalLoginAmr() {
        // The REFRESH_TOKEN context's principal is the Authentication stored in the OAuth2Authorization at
        // authorization_code time (SasRefreshTokenAuthenticationProvider) — the login event did not recur.
        JwtClaimsSet built = mint(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.REFRESH_TOKEN,
                session(list("pwd", "otp", "mfa")));

        assertThat(built.<List<String>>getClaim("amr")).containsExactly("pwd", "otp", "mfa");
    }

    @Test
    @DisplayName("소셜 로그인 세션 → amr=[] (계약에서 유일하게 [] 를 내는 자리 — 생략이 아니다)")
    void social_mintsEmptyArray() {
        JwtClaimsSet built = mint(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.AUTHORIZATION_CODE,
                session(list()));

        assertThat(built.getClaims()).containsKey("amr");
        assertThat(built.<List<String>>getClaim("amr")).isEmpty();
    }

    @Test
    @DisplayName("이 변경 전에 저장된 인가(details 에 amr 없음) → 클레임 생략(«2단계 없음» 으로 읽힌다) · 실패 아님")
    void storedBeforeChange_omitsClaim() {
        JwtClaimsSet built = mint(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.REFRESH_TOKEN,
                session(null));

        assertThat(built.getClaims()).doesNotContainKey("amr");
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("assume-tenant: subject 토큰의 amr 을 그대로 복사 (판정은 S4 — 여기선 가시성용)")
    void assumeTenant_copiesSubjectAmr() {
        JwtClaimsSet built = mintAssume(list("pwd", "otp", "mfa"));

        assertThat(built.<List<String>>getClaim("amr")).containsExactly("pwd", "otp", "mfa");
    }

    @Test
    @DisplayName("assume-tenant: subject 에 amr 이 없었으면 생략 (이 변경 전에 발급된 base 토큰)")
    void assumeTenant_subjectWithoutAmr_omits() {
        JwtClaimsSet built = mintAssume(null);

        assertThat(built.getClaims()).doesNotContainKey("amr");
    }

    @Test
    @DisplayName("workload assume · client_credentials → amr 없음 (워크로드는 사람으로 인증하지 않았다)")
    void workloadAndClientCredentials_neverCarryAmr() {
        JwtClaimsSet.Builder workloadClaims = claims();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(new WorkloadAssumeTenantAuthenticationToken(
                null, "artist-service-client", "fan-platform", "B2C_CONSUMER"));
        when(context.getClaims()).thenReturn(workloadClaims);
        customizer.customize(context);
        assertThat(workloadClaims.build().getClaims()).doesNotContainKey("amr");

        JwtClaimsSet.Builder ccClaims = claims();
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client());
        when(context.getClaims()).thenReturn(ccClaims);
        customizer.customize(context);
        assertThat(ccClaims.build().getClaims()).doesNotContainKey("amr");
    }

    private JwtClaimsSet mintAssume(ArrayList<String> subjectAmr) {
        JwtClaimsSet.Builder claims = claims();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(new AssumeTenantAuthenticationToken(
                null, "subject", "urn:ietf:params:oauth:token-type:access_token",
                "acme-corp", "B2B_ENTERPRISE", null, null, ACCOUNT, subjectAmr));
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);
        return claims.build();
    }

    private static JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("platform-console-web")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));
    }

    // ------------------------------------------------------------------ helpers

    private JwtClaimsSet mint(OAuth2TokenType tokenType, AuthorizationGrantType grantType,
                              UsernamePasswordAuthenticationToken principal) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("member@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));
        when(context.getTokenType()).thenReturn(tokenType);
        when(context.getAuthorizationGrantType()).thenReturn(grantType);
        when(context.getRegisteredClient()).thenReturn(client());
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claims);

        customizer.customize(context);
        return claims.build();
    }

    static UsernamePasswordAuthenticationToken session(ArrayList<String> amr) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "fan-platform");
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C_CONSUMER");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, ACCOUNT);
        details.put(PrincipalDetailKeys.EMAIL, "member@example.com");
        if (amr != null) {
            details.put(PrincipalDetailKeys.AMR, amr);
        }
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "member@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    static ArrayList<String> list(String... values) {
        return new ArrayList<>(List.of(values));
    }

    private static RegisteredClient client() {
        ClientSettings cs = ClientSettings.builder()
                .setting(OAuthClientMapper.SETTING_TENANT_ID, "fan-platform")
                .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C_CONSUMER")
                .build();
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("fan-platform-web")
                .clientName("Fan")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:3000/callback")
                .clientSettings(cs)
                .build();
    }
}
