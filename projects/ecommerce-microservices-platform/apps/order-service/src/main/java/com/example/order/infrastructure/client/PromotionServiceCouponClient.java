package com.example.order.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.order.application.exception.CouponRejectedException;
import com.example.order.application.exception.CouponServiceUnavailableException;
import com.example.order.application.port.CouponDiscountPort;
import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Set;
import java.util.function.Supplier;

/**
 * promotion-service adapter for {@link CouponDiscountPort} (TASK-INT-026).
 *
 * <p>Calls promotion-service directly on the internal network — never through the gateway, which
 * has no route for the release path. Explicit connect/read timeouts, a circuit breaker on apply
 * and a bounded retry (2 attempts) come from {@link ResilienceClientFactory}; 4xx is neither
 * retried nor counted by the breaker. Retrying apply is safe because promotion-service answers a
 * repeat for the same {@code orderId} idempotently.
 *
 * <p>Release deliberately skips the circuit breaker: the breaker opens exactly when apply calls
 * have been failing, which is when an orphaned coupon most needs its release attempt.
 */
@Slf4j
@Component
public class PromotionServiceCouponClient implements CouponDiscountPort {

    static final String APPLY_PATH = "/api/coupons/{couponId}/apply";
    static final String RELEASE_PATH = "/api/internal/coupons/{couponId}/release";

    private static final String USER_HEADER = "X-User-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /** promotion-service rejection codes passed through to the client unchanged. */
    private static final Set<String> PASSTHROUGH_CODES = Set.of(
            "COUPON_NOT_FOUND", "COUPON_ALREADY_USED", "COUPON_EXPIRED", "COUPON_NOT_OWNED");
    private static final String FALLBACK_REJECTION_CODE = "COUPON_NOT_APPLICABLE";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PromotionServiceCouponClient(
            @Value("${order.promotion-service.base-url:http://localhost:8092}") String baseUrl,
            @Value("${order.promotion-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${order.promotion-service.read-timeout-ms:3000}") int readTimeoutMs) {
        this(ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs),
                ResilienceClientFactory.buildCircuitBreaker("promotion-service"),
                ResilienceClientFactory.buildRetry("promotion-service", builder -> builder.maxAttempts(2)));
    }

    PromotionServiceCouponClient(RestClient restClient, CircuitBreaker circuitBreaker, Retry retry) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    @Override
    public long applyCoupon(String couponId, String orderId, String userId, long orderAmount) {
        String tenantId = TenantContext.currentTenant();
        Supplier<ApplyResponse> call = () -> restClient.post()
                .uri(APPLY_PATH, couponId)
                .header(USER_HEADER, userId)
                .header(TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApplyRequest(orderId, orderAmount))
                .retrieve()
                .body(ApplyResponse.class);
        try {
            ApplyResponse response =
                    Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call)).get();
            if (response == null) {
                throw new CouponServiceUnavailableException(
                        "promotion-service returned an empty coupon apply response", null);
            }
            return response.discountAmount();
        } catch (HttpClientErrorException e) {
            throw toRejection(e);
        } catch (CouponServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Coupon apply failed — promotion-service unavailable: couponId={}, orderId={}, cause={}",
                    couponId, orderId, e.toString());
            throw new CouponServiceUnavailableException("promotion-service did not answer the coupon apply", e);
        }
    }

    @Override
    public void releaseCoupon(String couponId, String orderId) {
        String tenantId = TenantContext.currentTenant();
        Runnable call = () -> restClient.post()
                .uri(RELEASE_PATH, couponId)
                .header(TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleaseRequest(orderId))
                .retrieve()
                .toBodilessEntity();
        try {
            Retry.decorateRunnable(retry, call).run();
            log.info("Coupon release requested for a placement that did not commit: couponId={}, orderId={}",
                    couponId, orderId);
        } catch (RuntimeException e) {
            // Nothing else can undo it from here: the coupon may stay USED by an order that was never saved.
            log.error("Coupon release failed — coupon may stay USED by an unsaved order: couponId={}, orderId={}",
                    couponId, orderId, e);
        }
    }

    private CouponRejectedException toRejection(HttpClientErrorException e) {
        ErrorBody body = parseErrorBody(e.getResponseBodyAsString());
        String code = body != null && body.code() != null && PASSTHROUGH_CODES.contains(body.code())
                ? body.code()
                : FALLBACK_REJECTION_CODE;
        String message = body != null && body.message() != null && !body.message().isBlank()
                ? body.message()
                : "Coupon cannot be applied";
        return new CouponRejectedException(code, message);
    }

    private ErrorBody parseErrorBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, ErrorBody.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    record ApplyRequest(String orderId, long orderAmount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApplyResponse(String couponId, long discountAmount, long finalAmount) {}

    record ReleaseRequest(String orderId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String code, String message) {}
}
