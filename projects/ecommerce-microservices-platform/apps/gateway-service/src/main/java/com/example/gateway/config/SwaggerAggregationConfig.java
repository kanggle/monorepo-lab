package com.example.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aggregated Swagger UI for the platform — proxies each downstream service's
 * {@code /v3/api-docs} behind a stable {@code /api-docs/<service>} gateway path so the
 * gateway's Swagger UI dropdown ({@code springdoc.swagger-ui.urls}) can render every
 * service's contract from a single entry point.
 *
 * <p><b>OFF by default (prod-safe).</b> Activated only when
 * {@code gateway.swagger-aggregation.enabled=true} (env {@code GATEWAY_SWAGGER_AGGREGATION_ENABLED}).
 * The k8s/compose {@code prod} profile does not set it, so a real production gateway never
 * exposes the aggregated service specs; the local demo overlay opts in explicitly.
 *
 * <p>Each route maps exactly one path to the downstream {@code /v3/api-docs} (via
 * {@code setPath}); these routes are additive to the YAML-defined {@code /api/**} routes
 * (Spring Cloud Gateway composes multiple {@link RouteLocator} beans).
 */
@Configuration
@ConditionalOnProperty(name = "gateway.swagger-aggregation.enabled", havingValue = "true")
public class SwaggerAggregationConfig {

    @Bean
    public RouteLocator apiDocsRouteLocator(
            RouteLocatorBuilder builder,
            @Value("${PRODUCT_SERVICE_URL:http://localhost:8082}") String productUri,
            @Value("${USER_SERVICE_URL:http://localhost:8084}") String userUri,
            @Value("${SEARCH_SERVICE_URL:http://localhost:8085}") String searchUri,
            @Value("${ORDER_SERVICE_URL:http://localhost:8086}") String orderUri,
            @Value("${PAYMENT_SERVICE_URL:http://localhost:8087}") String paymentUri,
            @Value("${SHIPPING_SERVICE_URL:http://localhost:8090}") String shippingUri,
            @Value("${REVIEW_SERVICE_URL:http://localhost:8091}") String reviewUri,
            @Value("${PROMOTION_SERVICE_URL:http://localhost:8092}") String promotionUri,
            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8093}") String notificationUri,
            @Value("${SETTLEMENT_SERVICE_URL:http://localhost:8094}") String settlementUri) {
        return builder.routes()
            .route("product-service-docs", r -> r.path("/api-docs/product-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(productUri))
            .route("user-service-docs", r -> r.path("/api-docs/user-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(userUri))
            .route("search-service-docs", r -> r.path("/api-docs/search-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(searchUri))
            .route("order-service-docs", r -> r.path("/api-docs/order-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(orderUri))
            .route("payment-service-docs", r -> r.path("/api-docs/payment-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(paymentUri))
            .route("shipping-service-docs", r -> r.path("/api-docs/shipping-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(shippingUri))
            .route("review-service-docs", r -> r.path("/api-docs/review-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(reviewUri))
            .route("promotion-service-docs", r -> r.path("/api-docs/promotion-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(promotionUri))
            .route("notification-service-docs", r -> r.path("/api-docs/notification-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(notificationUri))
            .route("settlement-service-docs", r -> r.path("/api-docs/settlement-service")
                .filters(f -> f.setPath("/v3/api-docs")).uri(settlementUri))
            .build();
    }
}
