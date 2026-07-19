package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteService 단위 테스트")
class RouteServiceTest {

    private final RouteService routeService = new RouteService();

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
