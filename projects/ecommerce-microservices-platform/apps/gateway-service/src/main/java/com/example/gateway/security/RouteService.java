package com.example.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Service
public class RouteService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public boolean isPublicRoute(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (HttpMethod.POST.equals(method)) {
            return "/api/auth/signup".equals(path)
                    || "/api/auth/login".equals(path)
                    || "/api/auth/refresh".equals(path);
        }
        if (HttpMethod.GET.equals(method)) {
            return PATH_MATCHER.match("/api/products/**", path)
                    || PATH_MATCHER.match("/api/search/**", path)
                    || PATH_MATCHER.match("/api/auth/oauth/**", path)
                    || PATH_MATCHER.match("/api/reviews/products/**", path)
                    || "/actuator/health".equals(path);
        }
        return false;
    }

    public String resolveTargetService(String path) {
        if (path.startsWith("/api/auth")) return "auth-service";
        if (path.startsWith("/api/users") || path.startsWith("/api/admin/users")) return "user-service";
        if (path.startsWith("/api/products") || path.startsWith("/api/admin/products")) return "product-service";
        if (path.startsWith("/api/search")) return "search-service";
        if (path.startsWith("/api/orders")) return "order-service";
        if (path.startsWith("/api/payments")) return "payment-service";
        if (path.startsWith("/api/shippings")) return "shipping-service";
        if (path.startsWith("/api/reviews")) return "review-service";
        if (path.startsWith("/api/promotions") || path.startsWith("/api/coupons")) return "promotion-service";
        if (path.startsWith("/api/notifications")) return "notification-service";
        if (path.startsWith("/api/wishlists")) return "user-service";
        return "unknown";
    }
}
