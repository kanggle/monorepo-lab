package com.example.order.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.order.application.exception.CouponRejectedException;
import com.example.order.application.exception.CouponServiceUnavailableException;
import com.example.order.domain.tenant.TenantContext;
import io.github.resilience4j.core.IntervalFunction;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PromotionServiceCouponClient — promotion-service 호출 번역 (TASK-INT-026)")
class PromotionServiceCouponClientTest {

    private MockWebServer server;
    private PromotionServiceCouponClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString().replaceAll("/$", "");
        client = new PromotionServiceCouponClient(
                ResilienceClientFactory.buildRestClient(baseUrl, 1000, 2000),
                ResilienceClientFactory.buildCircuitBreaker("promotion-service-test"),
                ResilienceClientFactory.buildRetry("promotion-service-test", builder -> builder
                        .maxAttempts(2)
                        .intervalFunction(IntervalFunction.of(Duration.ofMillis(1)))));
    }

    @AfterEach
    void tearDown() throws IOException {
        TenantContext.clear();
        server.shutdown();
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Test
    @DisplayName("200 이면 할인액을 돌려주고, 사용자·테넌트 헤더와 orderId·소계를 보낸다")
    void apply_ok_returnsDiscount_andSendsIdentityAndAmount() throws Exception {
        server.enqueue(json(200, "{\"couponId\":\"coupon-1\",\"discountAmount\":5000,\"finalAmount\":25000}"));
        TenantContext.set("tenant-a");

        long discount = client.applyCoupon("coupon-1", "order-1", "user-1", 30000L);

        assertThat(discount).isEqualTo(5000L);
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/coupons/coupon-1/apply");
        assertThat(request.getHeader("X-User-Id")).isEqualTo("user-1");
        assertThat(request.getHeader("X-Tenant-Id")).isEqualTo("tenant-a");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"orderId\":\"order-1\"").contains("\"orderAmount\":30000");
    }

    @Test
    @DisplayName("422 COUPON_ALREADY_USED 는 같은 코드로 거절되고 재시도하지 않는다")
    void apply_422_passesCodeThrough_withoutRetry() {
        server.enqueue(json(422, "{\"code\":\"COUPON_ALREADY_USED\",\"message\":\"Coupon has already been used\"}"));

        assertThatThrownBy(() -> client.applyCoupon("coupon-1", "order-1", "user-1", 30000L))
                .isInstanceOf(CouponRejectedException.class)
                .extracting("code").isEqualTo("COUPON_ALREADY_USED");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("404 COUPON_NOT_FOUND 도 같은 코드로 거절된다")
    void apply_404_passesCodeThrough() {
        server.enqueue(json(404, "{\"code\":\"COUPON_NOT_FOUND\",\"message\":\"not found\"}"));

        assertThatThrownBy(() -> client.applyCoupon("coupon-x", "order-1", "user-1", 30000L))
                .isInstanceOf(CouponRejectedException.class)
                .extracting("code").isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    @DisplayName("통과 목록에 없는 4xx 코드는 COUPON_NOT_APPLICABLE 로 번역한다")
    void apply_unknown4xx_becomesNotApplicable() {
        server.enqueue(json(400, "{\"code\":\"VALIDATION_ERROR\",\"message\":\"bad\"}"));

        assertThatThrownBy(() -> client.applyCoupon("coupon-1", "order-1", "user-1", 30000L))
                .isInstanceOf(CouponRejectedException.class)
                .extracting("code").isEqualTo("COUPON_NOT_APPLICABLE");
    }

    @Test
    @DisplayName("5xx 는 한 번 재시도한 뒤 서비스 불가로 번역한다")
    void apply_5xx_retriedOnce_thenUnavailable() {
        server.enqueue(json(500, "{\"code\":\"INTERNAL_ERROR\"}"));
        server.enqueue(json(500, "{\"code\":\"INTERNAL_ERROR\"}"));

        assertThatThrownBy(() -> client.applyCoupon("coupon-1", "order-1", "user-1", 30000L))
                .isInstanceOf(CouponServiceUnavailableException.class);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("release 는 게이트웨이 밖 내부 경로로 orderId 를 보낸다")
    void release_callsInternalPath() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.releaseCoupon("coupon-1", "order-1");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/internal/coupons/coupon-1/release");
        assertThat(request.getBody().readUtf8()).contains("\"orderId\":\"order-1\"");
    }

    @Test
    @DisplayName("release 실패는 던지지 않는다 — 배치 실패 경로를 더 망가뜨리지 않는다")
    void release_failure_isSwallowed() {
        server.enqueue(json(500, "{}"));
        server.enqueue(json(500, "{}"));

        assertThatCode(() -> client.releaseCoupon("coupon-1", "order-1")).doesNotThrowAnyException();
    }
}
