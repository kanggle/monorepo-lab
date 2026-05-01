package com.example.gateway.route;

import com.example.gateway.config.EdgeGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteConfig public-path 매칭")
class RouteConfigTest {

    private static RouteConfig routeConfigWith(List<String> publicPaths) {
        EdgeGatewayProperties props = new EdgeGatewayProperties();
        props.setPublicPaths(publicPaths);
        return new RouteConfig(props);
    }

    private static final List<String> ADMIN_PUBLIC_PATHS = List.of(
            "POST:/api/admin/auth/login",
            "POST:/api/admin/auth/2fa/enroll",
            "POST:/api/admin/auth/2fa/verify",
            "POST:/api/admin/auth/refresh",
            "GET:/.well-known/admin/jwks.json",
            "POST:/api/admin/accounts/*/lock",
            "POST:/api/admin/accounts/*/unlock",
            "POST:/api/admin/sessions/*/revoke",
            "GET:/api/admin/audit"
    );

    @ParameterizedTest(name = "{0} {1} → public")
    @CsvSource({
            "POST, /api/admin/auth/login",
            "POST, /api/admin/auth/2fa/enroll",
            "POST, /api/admin/auth/2fa/verify",
            "POST, /api/admin/auth/refresh",
            "GET,  /.well-known/admin/jwks.json",
            "POST, /api/admin/accounts/01HXYZ/lock",
            "POST, /api/admin/accounts/01HXYZ/unlock",
            "POST, /api/admin/sessions/01HXYZ/revoke",
            "GET,  /api/admin/audit"
    })
    @DisplayName("admin 서브트리는 gateway 층에서 public — operator JWT 검증은 downstream 전담")
    void adminSubtree_isPublic(String method, String path) {
        RouteConfig config = routeConfigWith(ADMIN_PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isTrue();
    }

    @ParameterizedTest(name = "{0} {1} → NOT public (method 다름)")
    @CsvSource({
            "GET,  /api/admin/auth/login",
            "GET,  /api/admin/auth/2fa/enroll",
            "POST, /.well-known/admin/jwks.json"
    })
    @DisplayName("admin public-path 은 method 를 정확히 일치시켜야 통과")
    void adminSubtree_wrongMethod_isNotPublic(String method, String path) {
        RouteConfig config = routeConfigWith(ADMIN_PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isFalse();
    }

    @ParameterizedTest(name = "{0} {1} → NOT public (admin subtree 외)")
    @CsvSource({
            "GET,  /api/admin/unknown",
            "POST, /api/admin/accounts/01HXYZ/suspend",
            "GET,  /api/admin/accounts/01HXYZ"
    })
    @DisplayName("admin public-paths 에 선언되지 않은 admin 경로는 여전히 인증 대상")
    void adminSubtree_nonAllowlistedPath_isNotPublic(String method, String path) {
        RouteConfig config = routeConfigWith(ADMIN_PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isFalse();
    }

    @ParameterizedTest(name = "기존 public 경로 {0} {1} 회귀 검증")
    @CsvSource({
            "POST, /api/auth/login",
            "POST, /api/accounts/signup",
            "POST, /api/auth/refresh",
            "GET,  /api/auth/oauth/authorize",
            "POST, /api/auth/oauth/callback"
    })
    @DisplayName("기존 public 경로는 이번 변경으로 깨지지 않는다")
    void existingPublicPaths_regression(String method, String path) {
        RouteConfig config = routeConfigWith(List.of(
                "POST:/api/auth/login",
                "POST:/api/accounts/signup",
                "POST:/api/auth/refresh",
                "GET:/api/auth/oauth/authorize",
                "POST:/api/auth/oauth/callback",
                "GET:/actuator/health"
        ));

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("/actuator/health 는 method 무관 public — 기존 동작 유지")
    void actuatorHealth_isAlwaysPublic() {
        RouteConfig config = routeConfigWith(List.of());

        assertThat(config.isPublicRoute(HttpMethod.GET, "/actuator/health")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/actuator/health")).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null method 는 public 아님")
    void nullMethod_isNotPublic() {
        RouteConfig config = routeConfigWith(ADMIN_PUBLIC_PATHS);

        assertThat(config.isPublicRoute(null, "/api/admin/auth/login")).isFalse();
    }

    // -----------------------------------------------------------------------
    // TASK-BE-251 Phase 2c: OAuth2 / OIDC standard endpoint routing (public paths)
    // -----------------------------------------------------------------------

    private static final List<String> OIDC_PUBLIC_PATHS = List.of(
            "GET:/oauth2/**",
            "POST:/oauth2/**",
            "GET:/.well-known/openid-configuration"
    );

    @ParameterizedTest(name = "OIDC/OAuth2 표준 경로 {0} {1} → public (JWT 검증 없이 통과)")
    @org.junit.jupiter.params.provider.CsvSource({
            // Discovery + JWKS
            "GET,  /.well-known/openid-configuration",
            "GET,  /oauth2/jwks",
            // Token endpoint (client_credentials, authorization_code, refresh_token)
            "POST, /oauth2/token",
            // Revocation (RFC 7009)
            "POST, /oauth2/revoke",
            // Introspection (RFC 7662)
            "POST, /oauth2/introspect",
            // UserInfo
            "GET,  /oauth2/userinfo",
            // Authorization endpoint
            "GET,  /oauth2/authorize"
    })
    @DisplayName("OIDC/OAuth2 표준 경로는 gateway JWT 검증 없이 통과 — auth-service(SAS)가 인증 처리")
    void oidcOAuth2Paths_arePublic(String method, String path) {
        RouteConfig config = routeConfigWith(OIDC_PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic)
                .as("OIDC/OAuth2 endpoint %s %s must be public in gateway (auth-service handles auth)"
                        .formatted(method.trim(), path.trim()))
                .isTrue();
    }

    @ParameterizedTest(name = "OIDC 경로 {0} {1} — method 불일치 시 public 아님")
    @org.junit.jupiter.params.provider.CsvSource({
            // POST /.well-known/openid-configuration is not listed — only GET is public
            "POST, /.well-known/openid-configuration",
            // DELETE on oauth2 paths is not declared
            "DELETE, /oauth2/token"
    })
    @DisplayName("OIDC public-paths 는 선언된 method만 통과")
    void oidcPaths_wrongMethod_isNotPublic(String method, String path) {
        RouteConfig config = routeConfigWith(OIDC_PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("OIDC public-paths 추가 후 기존 /api/auth/login 등 public 경로 회귀 없음")
    void oidcPublicPaths_regression_existingPublicPathsUnaffected() {
        List<String> combined = new java.util.ArrayList<>(OIDC_PUBLIC_PATHS);
        combined.addAll(List.of(
                "POST:/api/auth/login",
                "POST:/api/accounts/signup",
                "POST:/api/auth/refresh",
                "GET:/api/auth/oauth/authorize",
                "POST:/api/auth/oauth/callback"
        ));
        RouteConfig config = routeConfigWith(combined);

        assertThat(config.isPublicRoute(HttpMethod.POST, "/api/auth/login")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/api/accounts/signup")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/api/auth/refresh")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.GET, "/api/auth/oauth/authorize")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/api/auth/oauth/callback")).isTrue();
        // OIDC paths also present
        assertThat(config.isPublicRoute(HttpMethod.GET, "/.well-known/openid-configuration")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/oauth2/token")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/oauth2/revoke")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/oauth2/introspect")).isTrue();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("/oauth2/token 경로는 rate-limit 스코프 'refresh' 에 매핑됨")
    void oauth2TokenPath_rateLimitScope_isRefresh() {
        EdgeGatewayProperties props = new EdgeGatewayProperties();
        RouteConfig config = new RouteConfig(props);

        assertThat(config.resolveRateLimitScope("/oauth2/token"))
                .as("/oauth2/token must use the 'refresh' rate-limit bucket")
                .isEqualTo("refresh");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("/oauth2/revoke 경로는 global rate-limit 스코프 (null 반환)")
    void oauth2RevokePath_rateLimitScope_isGlobal() {
        EdgeGatewayProperties props = new EdgeGatewayProperties();
        RouteConfig config = new RouteConfig(props);

        assertThat(config.resolveRateLimitScope("/oauth2/revoke"))
                .as("/oauth2/revoke uses global rate-limit (null scope)")
                .isNull();
    }
}
