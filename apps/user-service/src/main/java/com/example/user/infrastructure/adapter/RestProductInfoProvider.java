package com.example.user.infrastructure.adapter;

import com.example.user.domain.service.ProductInfoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Profile("!standalone")
public class RestProductInfoProvider implements ProductInfoProvider {

    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;

    public RestProductInfoProvider(
            @Qualifier("wishlistRestTemplate") RestTemplate restTemplate,
            @Value("${product-service.base-url:http://localhost:8082}") String productServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.productServiceBaseUrl = productServiceBaseUrl;
    }

    @Override
    public Map<UUID, ProductInfo> getProductInfos(Set<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ProductInfo> result = new ConcurrentHashMap<>();

        CompletableFuture<?>[] futures = productIds.stream()
                .map(productId -> CompletableFuture.runAsync(() -> {
                    try {
                        ProductDetailResponse response = restTemplate.getForObject(
                                productServiceBaseUrl + "/api/products/{productId}",
                                ProductDetailResponse.class,
                                productId
                        );
                        if (response != null) {
                            result.put(productId, new ProductInfo(
                                    productId,
                                    response.name(),
                                    response.price(),
                                    response.status()
                            ));
                        } else {
                            result.put(productId, deletedProductInfo(productId));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch product info for productId={}: {}", productId, e.getMessage());
                        result.put(productId, deletedProductInfo(productId));
                    }
                }))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        return result;
    }

    private ProductInfo deletedProductInfo(UUID productId) {
        return new ProductInfo(productId, null, 0, "DELETED");
    }

    record ProductDetailResponse(
            String productId,
            String name,
            String description,
            String status,
            int price,
            String categoryId
    ) {
    }
}
