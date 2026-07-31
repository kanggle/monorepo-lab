package com.example.review.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.review.application.port.PurchaseVerificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Slf4j
@Component
public class OrderServiceClient implements PurchaseVerificationPort {

    private final RestClient restClient;

    /**
     * ADR-MONO-058 D7 (TASK-BE-570) — previously {@code RestClient.builder().baseUrl(baseUrl)
     * .build()} with no request factory (zero timeout): a hung order-service purchase-
     * verification call blocked this synchronous, user-facing review-creation request
     * indefinitely. Now built via {@link ResilienceClientFactory} with explicit, non-zero
     * connect/read timeouts.
     */
    public OrderServiceClient(
            @Value("${order-service.base-url}") String baseUrl,
            @Value("${order-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${order-service.read-timeout-ms:5000}") int readTimeoutMs) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public boolean hasUserPurchasedProduct(UUID userId, UUID productId) {
        try {
            VerifyPurchaseResponse response = restClient.get()
                    .uri("/api/orders/verify-purchase?productId={productId}", productId)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(VerifyPurchaseResponse.class);

            return response != null && response.purchased();
        } catch (Exception e) {
            log.error("Failed to verify purchase for user={} product={}", userId, productId, e);
            throw new RuntimeException("Purchase verification failed: order-service unavailable", e);
        }
    }

    record VerifyPurchaseResponse(boolean purchased) {}
}
