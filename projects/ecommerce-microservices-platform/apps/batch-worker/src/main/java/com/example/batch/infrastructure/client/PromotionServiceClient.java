package com.example.batch.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * HTTP client for promotion-service internal endpoints used by orphan-coupon reconciliation
 * (TASK-INT-028, {@code promotion-api.md}).
 *
 * <p>Internal network only — promotion-service's {@code /api/internal/**} carries no bearer (the same
 * as order-service's own release call). Explicit 5s connect / 10s read timeouts via
 * {@link ResilienceClientFactory}, mirroring {@link OrderServiceClient}. HTTP failures propagate; the
 * job decides what they mean.
 */
@Component
public class PromotionServiceClient {

    static final String STALE_USED_PATH = "/api/internal/coupons/stale-used";
    static final String RELEASE_PATH = "/api/internal/coupons/{couponId}/release";
    static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestClient restClient;

    public PromotionServiceClient(@Value("${promotion-service.base-url}") String baseUrl) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, 5_000, 10_000);
    }

    /**
     * Coupons {@code USED} longer than {@code olderThanMinutes}, across tenants.
     *
     * @throws IllegalStateException when promotion-service answered without a readable list
     */
    public List<StaleUsedCoupon> staleUsedCoupons(int olderThanMinutes, int limit) {
        StaleUsedCouponsResponse response = restClient.post()
                .uri(STALE_USED_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StaleUsedCouponsRequest(olderThanMinutes, limit))
                .retrieve()
                .body(StaleUsedCouponsResponse.class);
        if (response == null || response.coupons() == null) {
            throw new IllegalStateException("promotion-service returned no stale-used list");
        }
        return response.coupons();
    }

    /**
     * Releases {@code couponId} for {@code orderId}. The coupon lookup on the other side is
     * tenant-scoped, so {@code tenantId} must be the coupon's own tenant — without it any coupon
     * outside the default tenant would silently not be found.
     */
    public void release(String couponId, String orderId, String tenantId) {
        restClient.post()
                .uri(RELEASE_PATH, couponId)
                .header(TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleaseRequest(orderId))
                .retrieve()
                .toBodilessEntity();
    }

    public record StaleUsedCouponsRequest(int olderThanMinutes, int limit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StaleUsedCouponsResponse(List<StaleUsedCoupon> coupons) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StaleUsedCoupon(String couponId, String orderId, String tenantId, Instant usedAt) {}

    public record ReleaseRequest(String orderId) {}
}
