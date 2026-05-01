package com.example.gateway.route;

import com.example.gateway.config.EdgeGatewayProperties;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages public route resolution and path-to-scope mapping for rate limiting.
 */
@Component
public class RouteConfig {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Set<String> publicRoutes;

    public RouteConfig(EdgeGatewayProperties properties) {
        this.publicRoutes = new HashSet<>(properties.getPublicPaths());
    }

    /**
     * Checks if the given method + path combination is a public route (no auth required).
     */
    public boolean isPublicRoute(HttpMethod method, String path) {
        if (method == null) {
            return false;
        }
        String key = method.name() + ":" + path;
        for (String publicRoute : publicRoutes) {
            String[] parts = publicRoute.split(":", 2);
            if (parts.length == 2) {
                if (parts[0].equals(method.name()) && PATH_MATCHER.match(parts[1], path)) {
                    return true;
                }
            }
        }
        // actuator health is always public regardless of method
        if ("/actuator/health".equals(path)) {
            return true;
        }
        return false;
    }

    /**
     * Resolves the rate limit scope for the given path.
     * Returns null if no specific scope applies (only global scope).
     */
    public String resolveRateLimitScope(String path) {
        if (path.startsWith("/api/auth/login")) {
            return "login";
        }
        if (path.startsWith("/api/accounts/signup")) {
            return "signup";
        }
        if (path.startsWith("/api/auth/refresh")) {
            return "refresh";
        }
        // OIDC token endpoint shares the "refresh" rate-limit bucket for brute-force protection.
        // /oauth2/token is the standard equivalent of /api/auth/refresh for new OIDC clients.
        // TASK-BE-251 Phase 2c — apply same rate limit scope as legacy refresh endpoint.
        if (path.startsWith("/oauth2/token")) {
            return "refresh";
        }
        // admin login (/api/admin/auth/login) 은 현재 global 스코프만 적용 (전용 스코프 없음).
        // 전용 스코프 추가가 필요하다면 별도 태스크로 구현할 것.
        // 근거: TASK-BE-064 Scope(C) 결정 — 구현은 후속 태스크 분리.
        return null;
    }
}
