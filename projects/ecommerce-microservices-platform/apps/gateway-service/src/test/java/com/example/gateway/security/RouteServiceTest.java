package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteService 단위 테스트")
class RouteServiceTest {

    private final RouteService routeService = new RouteService();

    @ParameterizedTest
    @CsvSource({
            "POST, /api/auth/signup",
            "POST, /api/auth/login",
            "POST, /api/auth/refresh",
            "GET, /api/products/42",
            "GET, /api/products",
            "GET, /api/search/products?q=shoes",
            "GET, /actuator/health",
            "OPTIONS, /api/wishlists",
            "OPTIONS, /api/orders",
            "OPTIONS, /api/payments"
    })
    @DisplayName("공개 경로는 true를 반환한다")
    void isPublicRoute_publicPaths_returnsTrue(String method, String path) {
        boolean result = routeService.isPublicRoute(HttpMethod.valueOf(method), path);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("POST /api/shippings/carrier-webhook 은 공개 경로다 (TASK-BE-359, ADR-007 D5-2)")
    void isPublicRoute_carrierWebhook_post_returnsTrue() {
        boolean result = routeService.isPublicRoute(HttpMethod.POST, "/api/shippings/carrier-webhook");

        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/shippings/carrier-webhook",
            "PUT, /api/shippings/carrier-webhook",
            "DELETE, /api/shippings/carrier-webhook"
    })
    @DisplayName("carrier-webhook 경로라도 POST 이외의 메서드는 공개 경로가 아니다 (AC-3 과노출 방지)")
    void isPublicRoute_carrierWebhook_nonPost_returnsFalse(String method, String path) {
        boolean result = routeService.isPublicRoute(HttpMethod.valueOf(method), path);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/orders/123",
            "POST, /api/orders",
            "POST, /api/products",
            "GET, /api/users/me",
            "POST, /api/payments",
            // Other shipping paths must NOT be opened by BE-359 (AC-3)
            "GET, /api/shippings/orders/order-1",
            "PUT, /api/shippings/ship-1/status",
            "GET, /api/shippings",
            "POST, /api/shippings/ship-1/refresh-tracking"
    })
    @DisplayName("보호된 경로는 false를 반환한다")
    void isPublicRoute_protectedPaths_returnsFalse(String method, String path) {
        boolean result = routeService.isPublicRoute(HttpMethod.valueOf(method), path);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "/api/auth/login, auth-service",
            "/api/auth/refresh, auth-service",
            "/api/users/me, user-service",
            "/api/admin/users, user-service",
            "/api/products/42, product-service",
            "/api/admin/products, product-service",
            "/api/search/products, search-service",
            "/api/orders/123, order-service",
            "/api/payments/pay-1, payment-service",
            "/api/shippings/ship-1, shipping-service"
    })
    @DisplayName("경로에 따라 올바른 대상 서비스를 반환한다")
    void resolveTargetService_knownPaths_returnsCorrectService(String path, String expectedService) {
        String result = routeService.resolveTargetService(path);

        assertThat(result).isEqualTo(expectedService);
    }

    @Test
    @DisplayName("알 수 없는 경로는 unknown을 반환한다")
    void resolveTargetService_unknownPath_returnsUnknown() {
        String result = routeService.resolveTargetService("/api/unknown/endpoint");

        assertThat(result).isEqualTo("unknown");
    }
}
