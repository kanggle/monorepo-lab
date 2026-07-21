package com.example.gateway;

import com.example.gateway.testsupport.JwksMockServer;
import com.example.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Gateway 통합 테스트 (RSA/JWKS)")
class GatewayIntegrationTest {

    private static final JwtTestHelper jwtHelper = new JwtTestHelper();
    private static final JwksMockServer jwksMockServer;

    static {
        try {
            jwksMockServer = new JwksMockServer(jwtHelper);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopJwksMockServer() throws Exception {
        jwksMockServer.close();
    }

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                jwksMockServer::hostJwksUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    // -----------------------------------------------------------------------
    // 401 scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("인증 토큰 없이 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_noToken_returns401() {
        webTestClient.get()
                .uri("/api/orders/123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("잘못된 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_invalidToken_returns401() {
        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer invalid.token.value")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("만료된 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_expiredToken_returns401() {
        // signToken with -1 second TTL → already expired
        String expiredToken = jwtHelper.signToken(
                "user-123", null, -1L,
                java.util.Map.of(
                        "aud", List.of("ecommerce"),
                        "account_type", "CONSUMER",
                        "email", "user@example.com"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("audience가 일치하지 않는 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_wrongAudience_returns401() {
        // No aud claim → Spring Security rejects it when audiences is configured
        String noAudToken = jwtHelper.signToken(
                "user-123", "BUYER", 300L,
                java.util.Map.of("account_type", "CONSUMER", "email", "user@example.com"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + noAudToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -----------------------------------------------------------------------
    // Valid CONSUMER token scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("유효한 CONSUMER JWT(CUSTOMER role)로 보호된 경로 요청 시 JWT+admission 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void protectedRoute_validConsumerToken_passesJwtFilter() {
        // ADR-MONO-035 4b-2a: admission is role-based — a storefront consumer must
        // carry the CUSTOMER role (the account_type leg is removed). BUYER alone
        // would now 403 at AccountTypeEnforcementFilter, so use CUSTOMER to assert
        // genuine pass-through to the (unavailable) downstream.
        String token = jwtHelper.signConsumerToken("user-123", List.of("CUSTOMER"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("CONSUMER 토큰으로 /api/admin/ 경로 접근 시 403을 반환한다")
    void adminRoute_consumerToken_returns403() {
        String token = jwtHelper.signConsumerToken("user-123", List.of("BUYER"));

        webTestClient.get()
                .uri("/api/admin/products/42")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    // -----------------------------------------------------------------------
    // Spoofed identity header scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("보호된 경로에 스푸핑된 X-User-Id 헤더가 전달되어도 토큰 없으면 401을 반환한다")
    void protectedRoute_spoofedHeaders_noToken_returns401() {
        webTestClient.get()
                .uri("/api/orders/123")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Email", "spoofed@evil.com")
                .header("X-User-Role", "ECOMMERCE_OPERATOR")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -----------------------------------------------------------------------
    // Public route scenarios
    //
    // Auth (/api/auth/**) is no longer a gateway route — it was removed with
    // auth-service (TASK-BE-132); authentication moved to IAM/GAP OIDC
    // (TASK-MONO-027). The "public path bypasses the JWT filter" property is
    // covered below by GET /api/products/** (a real production public route);
    // the former /api/auth/* public-route tests asserted a route production no
    // longer serves and were removed (TASK-BE-555).
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("공개 경로 GET /api/products/**는 토큰 없이 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void publicRoute_products_passesWithoutToken() {
        webTestClient.get()
                .uri("/api/products/42")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 GET /api/search/**는 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_search_passesWithoutToken() {
        webTestClient.get()
                .uri("/api/search/products?q=shoes")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("/actuator/health 는 인증 없이 UP 상태를 반환한다")
    void actuatorHealth_returnsUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    // -----------------------------------------------------------------------
    // TASK-MONO-027 — TenantClaimValidator + AllowedIssuersValidator scenarios
    //
    // The integration profile (application-integration-test.yml) configures:
    //   ecommerce.oauth2.allowed-issuers = https://test.local/issuer,
    //                                      iam
    //   ecommerce.oauth2.required-tenant-id = ecommerce
    //
    // Spring Security WebFlux surfaces all JWT validation failures
    // (issuer mismatch, missing/blank tenant_id) as 401 via
    // ServerAuthenticationEntryPoint — not 403 — because the token is
    // rejected before authentication completes. Under entitlement-trust
    // (ADR-MONO-030 § 2.4) a non-blank tenant_id no longer fails the gate.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenant_id=ecommerce + iss=test.local → JWT 필터 통과 (다운스트림 불가 → 5xx)")
    void protectedRoute_ecommerceTenant_passesJwtFilter() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://test.local/issuer", "ecommerce");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("tenant_id=globex + entitled_domains ∋ ecommerce (고객 테넌트 운영자) → JWT 필터 통과")
    void protectedRoute_entitledForeignTenant_passesJwtFilter() {
        // The marketplace property (ADR-MONO-030 § D1-A): each customer-tenant runs its own store,
        // so its user's token names THEIR tenant, not this gateway's. It reaches this edge by
        // being entitled to ecommerce — the same way erp, finance, scm and wms are reached
        // (ADR-MONO-019 § D5). TASK-MONO-388.
        String token = jwtHelper.signTokenWithIssuerTenantAndEntitlements(
                "https://test.local/issuer", "globex", java.util.List.of("ecommerce"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("🔴 tenant_id=globex, ecommerce 미구독 → 거부. 이 한 줄이 TASK-MONO-388 의 전부다.")
    void protectedRoute_unentitledForeignTenant_isRefused() {
        // This exact token PASSED before TASK-MONO-388, and this suite asserted that it did —
        // the test was called protectedRoute_arbitraryTenant_passesJwtFilter and its comment
        // called it "entitlement-trust". It was not: the gate admitted any well-formed tenant_id,
        // which is a weaker question than entitlement asks, so a tenant entitled only to some
        // other domain walked in (TASK-BE-506).
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://test.local/issuer", "globex");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                // The gate rejects; SecurityConfig maps tenant_mismatch to 403 rather than 401,
                // because telling a client with a perfectly valid token to "re-authenticate"
                // would be a lie it can never act on (SecurityConfigTenantErrorMappingTest).
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .as("an unentitled foreign tenant must not reach ecommerce")
                                .isIn(401, 403));
    }

    @Test
    @DisplayName("tenant_id=wms, ecommerce 미구독 → 거부 (다른 도메인에만 구독된 테넌트)")
    void protectedRoute_tenantEntitledElsewhere_isRefused() {
        // Entitled to SOMETHING, just not to us. IAM issues a tenant_id only for an entitled
        // tenant, so "well-formed" felt like "entitled" — but entitled to WHICH domain was the
        // question nobody asked.
        String token = jwtHelper.signTokenWithIssuerTenantAndEntitlements(
                "https://test.local/issuer", "wms", java.util.List.of("wms"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403));
    }

    @Test
    @DisplayName("tenant_id 미설정 → 401")
    void protectedRoute_missingTenant_returns401() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://test.local/issuer", null);

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("iss=iam (legacy) + tenant_id=ecommerce → 통과")
    void protectedRoute_legacyIssuer_passesJwtFilter() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "iam", "ecommerce");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("iss=https://attacker.example.com → 401")
    void protectedRoute_attackerIssuer_returns401() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://attacker.example.com", "ecommerce");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-359 — carrier-webhook gateway public-route (ADR-007 D5-2)
    //
    // AC-2: gateway does NOT return 401 for POST /api/shippings/carrier-webhook
    //        without a JWT (public-route). The downstream shipping-service HMAC
    //        verifier handles authentication — the gateway only exposes the path.
    // AC-3: other /api/shippings/** paths remain JWT-protected.
    // AC-4: net-zero / fail-closed is enforced downstream (CarrierWebhookVerifier),
    //        not at the gateway — the gateway's job is only to NOT block the path.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-2: POST /api/shippings/carrier-webhook 은 JWT 없이도 게이트웨이 401을 반환하지 않는다 (공개 경로)")
    void carrierWebhook_noToken_gatewayDoesNotReturn401() {
        // The downstream shipping-service is unavailable in this test context,
        // so we expect a 5xx (connection refused) — NOT 401.
        // A 401 here would mean the gateway itself rejected the unauthenticated request,
        // which would violate AC-2.
        webTestClient.post()
                .uri("/api/shippings/carrier-webhook")
                .bodyValue("{\"deliveryId\":\"d1\",\"shippingId\":\"s1\",\"status\":\"DELIVERED\"}")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("AC-3: GET /api/shippings/orders/{orderId} 는 JWT 없으면 401 (다른 shipping 경로는 보호 유지)")
    void shippingRoute_otherPath_noToken_returns401() {
        webTestClient.get()
                .uri("/api/shippings/orders/order-123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("AC-3: POST /api/shippings/{id}/refresh-tracking 는 JWT 없으면 401 (과노출 없음)")
    void shippingRoute_refreshTracking_noToken_returns401() {
        webTestClient.post()
                .uri("/api/shippings/ship-123/refresh-tracking")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("AC-3: GET /api/shippings 는 JWT 없으면 401 (목록 조회 경로 보호 유지)")
    void shippingRoute_list_noToken_returns401() {
        webTestClient.get()
                .uri("/api/shippings")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-394 — CORS preflight (OPTIONS) must be permitted without auth.
    //
    // A browser sends an OPTIONS preflight (no Authorization header) before any
    // non-simple cross-origin request (e.g. an authed POST /api/wishlists with
    // Authorization + Content-Type: application/json). Before this fix the
    // security chain's anyExchange().authenticated() 401'd the preflight before
    // Spring Cloud Gateway's globalcors CorsWebFilter ran, so the browser
    // reported "TypeError: Failed to fetch" and every cross-origin authed write
    // was blocked. SecurityConfig now permits HttpMethod.OPTIONS so the
    // preflight reaches the CorsWebFilter, which answers 200 + Access-Control-*.
    //
    // globalcors config (application.yml, NOT overridden by the integration
    // profile): allowed-origins default http://localhost:3000, allow-credentials
    // true, allowed-methods/headers '*'.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC-1: CORS preflight(OPTIONS)는 인증 없이 200 + Access-Control-Allow-* 헤더를 반환한다 (보호 경로, TASK-BE-394)")
    void corsPreflight_protectedRoute_noToken_permittedWithCorsHeaders() {
        // OPTIONS preflight for a protected write path — no Authorization header.
        // Must NOT 401; the CorsWebFilter answers it with the allow headers.
        webTestClient.options()
                .uri("/api/orders/123")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
                .expectHeader().exists("Access-Control-Allow-Methods");
    }

    @Test
    @DisplayName("AC-1: 임의 /api/** 경로의 CORS preflight 도 인증 없이 200 을 반환한다 (preflight 전역 허용)")
    void corsPreflight_anyApiRoute_noToken_isPermitted() {
        // The user-service route owns /api/wishlists/** (application.yml) — the
        // exact path whose preflight-401 surfaced this bug (FE-074 AC-2).
        webTestClient.options()
                .uri("/api/wishlists")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }
}
