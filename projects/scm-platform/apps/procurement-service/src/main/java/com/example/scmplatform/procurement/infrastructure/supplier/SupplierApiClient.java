package com.example.scmplatform.procurement.infrastructure.supplier;

import com.example.common.resilience.ResilienceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin HTTP client wrapper around the supplier mock REST endpoint.
 *
 * <p>I1: explicit connect / read timeouts via
 * {@link ResilienceClientFactory#buildRestClient}. Circuit breaker / retry /
 * bulkhead are applied by Resilience4j Spring Boot annotations on
 * {@link RestSupplierAdapter} — keeping the client itself a thin transport.
 */
@Component
public class SupplierApiClient {

    private final RestClient client;

    public SupplierApiClient(
            @Value("${scmplatform.procurement.supplier.mock.base-url}") String baseUrl,
            @Value("${scmplatform.procurement.supplier.mock.connect-timeout-ms:2000}") int connectMs,
            @Value("${scmplatform.procurement.supplier.mock.read-timeout-ms:10000}") int readMs) {
        this.client = ResilienceClientFactory.buildRestClient(baseUrl, connectMs, readMs);
    }

    /**
     * Submit a PO payload to the supplier endpoint with the given idempotency
     * key. The supplier MUST return a JSON body shaped like
     * {@code {"receiptRef": "...", "status": "..."}}. Non-2xx responses are
     * surfaced as {@link org.springframework.web.client.HttpClientErrorException}
     * (4xx — never retry, I3) or
     * {@link org.springframework.web.client.HttpServerErrorException} (5xx —
     * retried with exponential backoff by the caller's Resilience4j wrapper).
     */
    public Map<String, Object> postPurchaseOrder(String idempotencyKey, Map<String, Object> body) {
        return client.post()
                .uri("/v1/purchase-orders")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // Translate to a 4xx exception so Resilience4j ignores it
                    // for retry purposes (per ResilienceClientFactory.standardRetryConfig).
                    throw new org.springframework.web.client.HttpClientErrorException(
                            res.getStatusCode());
                })
                .body(MAP_TYPE);
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {};
}
