package com.example.review.infrastructure.client;

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

    public OrderServiceClient(@Value("${order-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
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
