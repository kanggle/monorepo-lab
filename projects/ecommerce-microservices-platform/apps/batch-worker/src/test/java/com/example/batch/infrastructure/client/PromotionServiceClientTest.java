package com.example.batch.infrastructure.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PromotionServiceClient 단위 테스트 (TASK-INT-028)")
class PromotionServiceClientTest {

    private MockWebServer promotionService;
    private PromotionServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        promotionService = new MockWebServer();
        promotionService.start();
        client = new PromotionServiceClient(
                "http://" + promotionService.getHostName() + ":" + promotionService.getPort());
    }

    @AfterEach
    void tearDown() throws IOException {
        promotionService.shutdown();
    }

    @Test
    @DisplayName("stale-used 는 요청값을 보내고 쿠폰별 테넌트까지 읽는다")
    void staleUsed_sendsParams_andReadsTenant() throws Exception {
        promotionService.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"coupons\":[{\"couponId\":\"c-1\",\"orderId\":\"o-1\",\"tenantId\":\"tenant-b\","
                        + "\"usedAt\":\"2026-09-16T00:00:00Z\"}]}"));

        List<PromotionServiceClient.StaleUsedCoupon> coupons = client.staleUsedCoupons(60, 200);

        assertThat(coupons).singleElement().satisfies(c -> {
            assertThat(c.couponId()).isEqualTo("c-1");
            assertThat(c.tenantId()).isEqualTo("tenant-b");
        });
        RecordedRequest request = promotionService.takeRequest();
        assertThat(request.getPath()).isEqualTo(PromotionServiceClient.STALE_USED_PATH);
        assertThat(request.getBody().readUtf8()).contains("\"olderThanMinutes\":60").contains("\"limit\":200");
    }

    @Test
    @DisplayName("본문 없는 stale-used 응답은 예외다")
    void staleUsed_emptyBody_throws() {
        promotionService.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.staleUsedCoupons(60, 200)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("release 는 쿠폰 자신의 테넌트를 X-Tenant-Id 로 싣고 orderId 를 보낸다")
    void release_sendsCouponsTenantHeader_andOrderId() throws Exception {
        promotionService.enqueue(new MockResponse().setResponseCode(204));

        client.release("c-1", "o-1", "tenant-b");

        RecordedRequest request = promotionService.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/internal/coupons/c-1/release");
        assertThat(request.getHeader(PromotionServiceClient.TENANT_HEADER)).isEqualTo("tenant-b");
        assertThat(request.getBody().readUtf8()).contains("\"orderId\":\"o-1\"");
    }

    @Test
    @DisplayName("release 5xx 는 예외다 — 잡이 건별 실패로 센다")
    void release_serverError_throws() {
        promotionService.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.release("c-1", "o-1", "ecommerce")).isInstanceOf(RuntimeException.class);
    }
}
