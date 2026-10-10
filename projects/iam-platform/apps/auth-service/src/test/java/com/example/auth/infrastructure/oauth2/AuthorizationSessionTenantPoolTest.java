package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.session.PrincipalDetailKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-615 — {@link AuthorizationSessionTenant}, the one rule the claim, the refresh mirror-row
 * comparison and the authorize gate share, for a consumer-pool principal. The site is the CLIENT's:
 * a store authorization answers {@code ecommerce}, a fan authorization {@code fan-platform} — so a
 * store refresh row can never satisfy a fan comparison (AC-5). Per-site principals: unchanged.
 */
@DisplayName("AuthorizationSessionTenant — 풀 principal (TASK-BE-615)")
class AuthorizationSessionTenantPoolTest {

    private static Authentication principal(String tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C_CONSUMER");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, "acc-pool");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "pool@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        return token;
    }

    private static OAuth2Authorization authorizationFor(String clientId, Authentication principal) {
        RegisteredClient client = RegisteredClient.withId(clientId + "-id").clientId(clientId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb").build();
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName("pool@example.com")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .attribute(Principal.class.getName(), principal)
                .build();
    }

    @Test
    @DisplayName("풀 principal → 요청 client 의 사이트 (스토어=ecommerce, 팬=fan-platform)")
    void poolPrincipal_isTheClientsSite() {
        Authentication pool = principal("consumer-pool");
        assertThat(AuthorizationSessionTenant.of(pool, "ecommerce")).isEqualTo("ecommerce");
        assertThat(AuthorizationSessionTenant.of(pool, "fan-platform")).isEqualTo("fan-platform");
        assertThat(AuthorizationSessionTenant.of(pool, " ecommerce ")).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("AC-5: refresh 의 비교값 — 스토어 인가는 ecommerce, 팬 인가는 fan-platform (서로 다르다)")
    void refreshComparison_isTheAuthorizationsSite() {
        Authentication pool = principal("consumer-pool");
        assertThat(AuthorizationSessionTenant.of(authorizationFor("store", pool), "ecommerce")).isEqualTo("ecommerce");
        assertThat(AuthorizationSessionTenant.of(authorizationFor("fan", pool), "fan-platform"))
                .isEqualTo("fan-platform");
    }

    /**
     * TASK-MONO-772 S4 — CHANGED EXPECTATION. Under TASK-BE-615 the console row read «not mapped (pool value)».
     * A faceted pool operator's console token now carries {@code iam}, so the mirror rows do too, and the refresh
     * comparison must answer {@code iam}. The site mapping ({@code mapsPoolPrincipalTo}) still excludes the
     * console — that is a separate branch (772 AC-0 F4).
     */
    @Test
    @DisplayName("772 S4: 콘솔(iam) client → 세션 테넌트 iam (풀-사이트 매핑과 별개 갈래) · 인가·refresh 비교값 모두")
    void console_mapsToIam_separateBranch() {
        Authentication pool = principal("consumer-pool");
        assertThat(AuthorizationSessionTenant.of(pool, "iam")).isEqualTo("iam");
        assertThat(AuthorizationSessionTenant.of(pool, " iam ")).isEqualTo("iam");
        assertThat(AuthorizationSessionTenant.of(authorizationFor("platform-console-web", pool), "iam"))
                .isEqualTo("iam");
        assertThat(AuthorizationSessionTenant.mapsPoolPrincipalTo("iam"))
                .as("the consumer-site mapping still excludes the console — no re-login loop").isFalse();
        assertThat(AuthorizationSessionTenant.mapsPoolPrincipalToConsole("iam")).isTrue();
        assertThat(AuthorizationSessionTenant.mapsPoolPrincipalToConsole("ecommerce")).isFalse();
    }

    @Test
    @DisplayName("풀 테넌트 client · client 테넌트 없음 → 사상하지 않는다(풀 값 그대로 — 발급은 거절된다)")
    void poolOrNoClient_notMapped() {
        Authentication pool = principal("consumer-pool");
        assertThat(AuthorizationSessionTenant.of(pool, "consumer-pool")).isEqualTo("consumer-pool");
        assertThat(AuthorizationSessionTenant.of(pool, null)).isEqualTo("consumer-pool");
    }

    @Test
    @DisplayName("대조군: 사이트 계정 principal 은 그대로 자기 테넌트 (BE-604 규칙 무변경)")
    void sitePrincipal_unchanged() {
        Authentication store = principal("ecommerce");
        assertThat(AuthorizationSessionTenant.of(store, "fan-platform")).isEqualTo("ecommerce");
        assertThat(AuthorizationSessionTenant.of(store, "iam")).isEqualTo("ecommerce");
        assertThat(AuthorizationSessionTenant.isPoolPrincipal(store)).isFalse();
        assertThat(AuthorizationSessionTenant.isPoolPrincipal(principal("consumer-pool"))).isTrue();
    }
}
