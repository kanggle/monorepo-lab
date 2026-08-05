package com.example.gateway.route;

import com.example.gateway.config.EdgeGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteConfig public-path 매칭")
class RouteConfigTest {

    private static RouteConfig routeConfigWith(List<String> publicPaths) {
        EdgeGatewayProperties props = new EdgeGatewayProperties();
        props.setPublicPaths(publicPaths);
        return new RouteConfig(props);
    }

    /**
     * {@code gateway.public-paths} read from the REAL {@code application.yml} — the
     * single fixture for every case in this class (TASK-MONO-508).
     *
     * <p><strong>These used to be three hand-copied literals, and that is exactly how
     * the defect survived.</strong> The admin copy drifted from the file it claimed to
     * mirror — it was missing {@code GET:/api/admin/accounts/**},
     * {@code POST:/api/admin/auth/token-exchange} and
     * {@code GET:/api/admin/console/registry} — and it went further than drift: a case
     * named {@code adminSubtree_nonAllowlistedPath_isNotPublic} <em>asserted</em> that
     * {@code GET /api/admin/accounts/{id}} is NOT public, contradicting both the
     * shipped config and {@code gateway-api.md § Admin Routes}. So the suite stayed
     * green while the edge 401'd the console's entire IAM administration section, and
     * the one test whose <em>name</em> carried the invariant ("admin 서브트리는 …
     * public") proved it only of the nine paths someone had remembered to copy.
     *
     * <p>A fixture that duplicates the artifact can only ever test the duplicate.
     * Reading the file means a narrowing edit to {@code application.yml} turns this
     * suite red instead of silently un-delegating the subtree.
     */
    private static final List<String> PUBLIC_PATHS = publicPathsFromYaml();

    @SuppressWarnings("unchecked")
    private static List<String> publicPathsFromYaml() {
        try (java.io.InputStream in =
                     RouteConfigTest.class.getResourceAsStream("/application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(in);
            Map<String, Object> gateway = (Map<String, Object>) root.get("gateway");
            List<String> paths = (List<String>) gateway.get("public-paths");
            // An empty read must fail loudly: a silently empty allowlist would make
            // every "NOT public" assertion below pass for the wrong reason.
            assertThat(paths).as("gateway.public-paths in application.yml").isNotEmpty();
            return paths;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read application.yml", e);
        }
    }

    /**
     * The platform invariant of {@code gateway-api.md § Admin Routes}: the gateway runs
     * NO JWT verification anywhere under {@code /api/admin/**} or
     * {@code /.well-known/admin/**}. Operator tokens are signed by admin-service under
     * its own key pair, so the gateway cannot verify them; {@code
     * OperatorAuthenticationFilter} is the single verification point and already covers
     * the whole subtree.
     *
     * <p>The paths below are deliberately ones nobody enumerated — including endpoints
     * that were measured returning 401 on the integrated demo before this was fixed.
     */
    @ParameterizedTest(name = "{0} {1} → public")
    @CsvSource({
            // the endpoints that WERE enumerated (regression)
            "POST, /api/admin/auth/login",
            "POST, /api/admin/auth/token-exchange",
            "POST, /api/admin/auth/2fa/enroll",
            "POST, /api/admin/auth/2fa/verify",
            "POST, /api/admin/auth/refresh",
            "GET,  /.well-known/admin/jwks.json",
            "POST, /api/admin/accounts/01HXYZ/lock",
            "POST, /api/admin/accounts/01HXYZ/unlock",
            "POST, /api/admin/sessions/01HXYZ/revoke",
            "GET,  /api/admin/audit",
            "GET,  /api/admin/console/registry",
            // the endpoints that were NOT — measured 401 TOKEN_INVALID on the demo
            "GET,  /api/admin/org-nodes",
            "GET,  /api/admin/me",
            "GET,  /api/admin/operators",
            "GET,  /api/admin/operators/grantable-roles",
            "GET,  /api/admin/roles",
            "GET,  /api/admin/permissions",
            "GET,  /api/admin/groups",
            "GET,  /api/admin/partnerships",
            // every verb admin-service maps, at subtree depth
            "POST,   /api/admin/org-nodes",
            "PATCH,  /api/admin/org-nodes/01HXYZ",
            "PUT,    /api/admin/org-nodes/01HXYZ/ceiling",
            "DELETE, /api/admin/org-nodes/01HXYZ/admins/01HABC",
            "PUT,    /api/admin/operators/01HXYZ/assignments/acme/org-scope",
            "DELETE, /api/admin/groups/01HXYZ/grants/01HABC",
            "POST,   /api/admin/onboarding/organizations",
            "POST,   /api/admin/subscriptions",
            // a path nobody has written yet — the invariant is about the subtree,
            // not about today's controller list
            "GET,  /api/admin/not-invented-yet/deep/path"
    })
    @DisplayName("admin 서브트리 전체가 gateway 층에서 public — operator JWT 검증은 downstream 전담")
    void adminSubtree_isPublic(String method, String path) {
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic)
                .as("gateway-api.md § Admin Routes: %s %s must reach admin-service "
                        + "without gateway JWT verification", method.trim(), path.trim())
                .isTrue();
    }

    /**
     * The delegation is scoped to the admin subtree and does not leak outward. A
     * neighbouring path that merely starts with the same prefix stays authenticated —
     * this is what stops the fix from becoming "everything is public".
     */
    @ParameterizedTest(name = "{0} {1} → NOT public (admin 서브트리 밖)")
    @CsvSource({
            "GET,  /api/adminx",
            "GET,  /api/accounts/me",
            "POST, /api/tenants",
            "GET,  /.well-known/openid-configuration-not"
    })
    @DisplayName("admin 위임은 서브트리 밖으로 새지 않는다")
    void outsideAdminSubtree_isNotPublic(String method, String path) {
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isFalse();
    }

    /**
     * The bare subtree root is delegated too, and that is not an accident of notation:
     * Spring's {@code AntPathMatcher} lets {@code /**} match zero segments, so
     * {@code /api/admin/**} matches {@code /api/admin} itself. This is asserted rather
     * than left implicit because it is a real boundary and it is worth stating which
     * way it falls.
     *
     * <p>It costs nothing: no admin-service controller maps {@code /api/admin} exactly,
     * so the request 404s downstream, and admin-service's own
     * {@code OperatorAuthenticationFilter} draws the boundary in the same place — its
     * {@code shouldNotFilter} ends in {@code !path.startsWith("/api/admin/")}, which is
     * also {@code true} for the bare root. The two layers therefore agree; a path where
     * the edge and the service disagreed about who authenticates is the failure mode
     * worth preventing, and this is not one.
     *
     * <p>The same already held before this change — {@code GET:/api/admin/accounts/**}
     * matched {@code /api/admin/accounts} under the previous enumeration.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("서브트리 루트(/api/admin)도 위임 대상 — 두 층의 경계가 일치한다")
    void adminSubtreeRoot_isPublic() {
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        assertThat(config.isPublicRoute(HttpMethod.GET, "/api/admin")).isTrue();
    }

    @ParameterizedTest(name = "기존 public 경로 {0} {1} 회귀 검증")
    @CsvSource({
            "POST, /api/accounts/signup",
            "POST, /api/auth/refresh"
    })
    @DisplayName("기존 public 경로는 이번 변경으로 깨지지 않는다")
    void existingPublicPaths_regression(String method, String path) {
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isTrue();
    }

    @ParameterizedTest(name = "일몰된 레거시 경로 {0} {1} → NOT public")
    @CsvSource({
            "POST, /api/auth/login",
            "GET,  /api/auth/oauth/authorize",
            "POST, /api/auth/oauth/callback"
    })
    @DisplayName("TASK-BE-398 — 일몰된 레거시 커스텀-JWT 경로는 더 이상 public 이 아니다")
    void sunsetLegacyPaths_areNotPublic(String method, String path) {
        // The endpoints are gone from auth-service; leaving them on the edge allowlist
        // would keep an unauthenticated hole open onto a route that no longer exists.
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isFalse();
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
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        assertThat(config.isPublicRoute(null, "/api/admin/auth/login")).isFalse();
    }

    // -----------------------------------------------------------------------
    // TASK-BE-251 Phase 2c: OAuth2 / OIDC standard endpoint routing (public paths)
    // -----------------------------------------------------------------------

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
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

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
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        boolean isPublic = config.isPublicRoute(HttpMethod.valueOf(method.trim()), path.trim());

        assertThat(isPublic).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("OIDC public-paths 와 나머지 public 경로가 서로를 가리지 않는다")
    void oidcPublicPaths_regression_existingPublicPathsUnaffected() {
        RouteConfig config = routeConfigWith(PUBLIC_PATHS);

        assertThat(config.isPublicRoute(HttpMethod.POST, "/api/accounts/signup")).isTrue();
        assertThat(config.isPublicRoute(HttpMethod.POST, "/api/auth/refresh")).isTrue();
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
