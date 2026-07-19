package com.example.batch.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * HTTP client for order-service internal endpoint (TASK-BE-413 / AC-2).
 *
 * <p>Calls {@code POST /api/internal/orders/confirm-paid-stale} with a
 * {@code client_credentials} Bearer token obtained from
 * {@link IamClientCredentialsTokenProvider}. Request body carries
 * {@code olderThanMinutes} and {@code limit} (both config-driven with defaults 30 / 200).
 * Response is {@code { scanned, confirmed, skipped, confirmedOrderIds? }}.
 *
 * <p>Mirrors {@link ProductServiceClient} (BE-409): explicit 5s connect / 10s read timeouts,
 * {@code @JsonIgnoreProperties(ignoreUnknown=true)} on all response DTOs, HTTP failures
 * propagate as unchecked exceptions and are caught by the job to record a FAILED history
 * entry — they do NOT propagate further (BE-409 isolation rule).
 *
 * <p>Config key: {@code order-service.base-url}.
 */
@Slf4j
@Component
public class OrderServiceClient {

    static final String CONFIRM_PAID_STALE_PATH = "/api/internal/orders/confirm-paid-stale";

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final int defaultOlderThanMinutes;
    private final int defaultLimit;

    public OrderServiceClient(
            @Value("${order-service.base-url}") String baseUrl,
            @Value("${batch.jobs.stale-paid-order-confirmation.older-than-minutes:30}") int defaultOlderThanMinutes,
            @Value("${batch.jobs.stale-paid-order-confirmation.limit:200}") int defaultLimit,
            IamClientCredentialsTokenProvider tokenProvider) {
        // Explicit timeouts: prevent a hung order-service from blocking the scheduler thread
        // for the entire ShedLock window. 5s connect / 10s read.
        this.restClient = RestClients.timed(Duration.ofSeconds(5), Duration.ofSeconds(10))
                .baseUrl(baseUrl)
                .build();
        this.tokenProvider = tokenProvider;
        this.defaultOlderThanMinutes = defaultOlderThanMinutes;
        this.defaultLimit = defaultLimit;
    }

    /**
     * Call the stale paid-order forward-confirm endpoint using config-driven defaults.
     *
     * @return the tally returned by order-service; never null
     */
    public ConfirmPaidStaleResponse confirmPaidStale() {
        return confirmPaidStale(defaultOlderThanMinutes, defaultLimit);
    }

    /**
     * Call the stale paid-order forward-confirm endpoint with explicit parameters.
     *
     * @param olderThanMinutes orders younger than this are NOT swept
     * @param limit            max orders processed per call
     * @return the tally returned by order-service; never null
     */
    public ConfirmPaidStaleResponse confirmPaidStale(int olderThanMinutes, int limit) {
        String bearer = tokenProvider.currentBearer();
        log.debug("Calling order-service confirm-paid-stale (olderThanMinutes={} limit={})",
                olderThanMinutes, limit);
        ConfirmPaidStaleResponse response = restClient.post()
                .uri(CONFIRM_PAID_STALE_PATH)
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmPaidStaleRequest(olderThanMinutes, limit))
                .retrieve()
                .body(ConfirmPaidStaleResponse.class);
        return response != null
                ? response
                : new ConfirmPaidStaleResponse(0, 0, 0, List.of());
    }

    /**
     * Request body for {@code POST /api/internal/orders/confirm-paid-stale}
     * (order-confirm-paid-stale.md § Request).
     *
     * @param olderThanMinutes orders younger than this are skipped (must be ≥ 1)
     * @param limit            max orders to process per call (1..1000)
     */
    public record ConfirmPaidStaleRequest(int olderThanMinutes, int limit) {}

    /**
     * Response shape for {@code POST /api/internal/orders/confirm-paid-stale}
     * (order-confirm-paid-stale.md § Response 200).
     *
     * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} guards against future
     * additions to the response contract (mirrors BE-409 lesson from ProductServiceClient).
     *
     * @param scanned           orders matched by the server-side predicate this call
     * @param confirmed         orders actually transitioned PENDING → CONFIRMED
     * @param skipped           orders no-op'd (already confirmed or raced out of PENDING)
     * @param confirmedOrderIds ids confirmed this call (optional — may be empty)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfirmPaidStaleResponse(
            int scanned,
            int confirmed,
            int skipped,
            List<String> confirmedOrderIds) {
    }
}
