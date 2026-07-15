package com.example.gateway.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route-map resolution test for {@code application.yml}.
 *
 * <p>Spring Cloud Gateway matches routes in <b>declaration order</b>. TASK-BE-508:
 * the user session-management endpoints ({@code /api/accounts/me/sessions**}) are
 * served by auth-service, but share the {@code /api/accounts/**} prefix owned by
 * account-service. If the session route is not declared BEFORE the account route,
 * session calls misroute to account-service (→ NoResourceFoundException → 500).
 *
 * <p>This test parses the real {@code application.yml} route list and, using the
 * same {@link PathPattern} matcher Spring Cloud Gateway uses, resolves a path to
 * the first route whose {@code Path} predicate matches — exactly the runtime
 * behaviour. It needs no Spring context and no Docker.
 */
@DisplayName("Gateway 라우트 순서 해석 (application.yml)")
class GatewayRouteResolutionTest {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private record Route(String id, List<PathPattern> patterns) {
    }

    private static final List<Route> ROUTES = loadRoutes();

    @SuppressWarnings("unchecked")
    private static List<Route> loadRoutes() {
        try (InputStream in = GatewayRouteResolutionTest.class
                .getClassLoader().getResourceAsStream("application.yml")) {
            if (in == null) {
                throw new IllegalStateException("application.yml not found on classpath");
            }
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
            Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
            List<Map<String, Object>> rawRoutes = (List<Map<String, Object>>) gateway.get("routes");

            List<Route> routes = new ArrayList<>();
            for (Map<String, Object> raw : rawRoutes) {
                String id = (String) raw.get("id");
                List<String> predicates = (List<String>) raw.get("predicates");
                List<PathPattern> patterns = new ArrayList<>();
                for (String predicate : predicates) {
                    if (predicate.startsWith("Path=")) {
                        String value = predicate.substring("Path=".length());
                        for (String pattern : value.split(",")) {
                            patterns.add(PARSER.parse(pattern.trim()));
                        }
                    }
                }
                routes.add(new Route(id, patterns));
            }
            return routes;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load routes from application.yml", e);
        }
    }

    /** Mirrors Spring Cloud Gateway: first route (in declaration order) whose Path predicate matches. */
    private static String resolve(String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (Route route : ROUTES) {
            for (PathPattern pattern : route.patterns()) {
                if (pattern.matches(container)) {
                    return route.id();
                }
            }
        }
        return null;
    }

    @ParameterizedTest(name = "{0} → auth-service-sessions")
    @CsvSource({
            "/api/accounts/me/sessions",
            "/api/accounts/me/sessions/current",
            "/api/accounts/me/sessions/01936c2f-7d8a-7c3e-9b4a-1f2e3d4c5b6a"
    })
    @DisplayName("세션 4경로는 auth-service-sessions 라우트로 해석 (account-service 로 오배송 아님)")
    void sessionPaths_resolveToAuthServiceSessionsRoute(String path) {
        assertThat(resolve(path))
                .as("%s must route to auth-service (AccountSessionController), not account-service", path)
                .isEqualTo("auth-service-sessions");
    }

    @ParameterizedTest(name = "{0} → account-service (회귀 없음)")
    @CsvSource({
            "/api/accounts/me",
            "/api/accounts/me/profile",
            "/api/accounts/me/status",
            "/api/accounts/signup"
    })
    @DisplayName("프로필/상태/가입 경로는 여전히 account-service — 세션 라우트가 훔치지 않음")
    void accountPaths_stillResolveToAccountService(String path) {
        assertThat(resolve(path))
                .as("%s must still route to account-service (session route must not steal it)", path)
                .isEqualTo("account-service");
    }

    @Test
    @DisplayName("auth-service-sessions 라우트는 account-service 라우트보다 먼저 선언됨 (순서 함정 방지)")
    void sessionRoute_declaredBeforeAccountRoute() {
        int sessionIdx = indexOf("auth-service-sessions");
        int accountIdx = indexOf("account-service");

        assertThat(sessionIdx).as("auth-service-sessions route must exist").isGreaterThanOrEqualTo(0);
        assertThat(accountIdx).as("account-service route must exist").isGreaterThanOrEqualTo(0);
        assertThat(sessionIdx)
                .as("auth-service-sessions must be declared BEFORE account-service or /api/accounts/** swallows it")
                .isLessThan(accountIdx);
    }

    private static int indexOf(String routeId) {
        for (int i = 0; i < ROUTES.size(); i++) {
            if (ROUTES.get(i).id().equals(routeId)) {
                return i;
            }
        }
        return -1;
    }
}
