package com.example.fanplatform.artist.adapter.out.store;

import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * The real {@link StoreSellerDirectory} — TASK-MONO-759 (ADR-MONO-079 D2; owner decisions
 * 2026-10-03: 갈래 A + reach path R1). Replaces {@code UnwiredStoreSellerDirectory}, which
 * answered «cannot verify» to everything.
 *
 * <pre>
 * GET {store-base-url}/internal/sellers/{sellerId}       (the ecommerce gateway)
 *   Authorization: Bearer &lt;artist-service-client token, assumed for tenant ecommerce&gt;
 * → 200 { "sellerId", "status" }   |   404 { "code": "SELLER_NOT_FOUND" }
 * </pre>
 *
 * <h2>Mapping — the port's whole contract ({@code artist-api.md} § Store seller verification)</h2>
 *
 * <ul>
 *   <li>200 whose {@code sellerId} is the one asked for and whose {@code status} is a known
 *       store {@code SellerStatus} name → {@code Optional.of(status)};</li>
 *   <li>404 whose body {@code code} is {@code SELLER_NOT_FOUND} → {@code Optional.empty()} —
 *       the ONLY definite «no such seller». A 404 without that code is a route or proxy miss,
 *       not an answer about the seller;</li>
 *   <li><b>everything else</b> → {@link StoreSellerLookupUnavailableException}: any other
 *       status (401/403 from either the gateway or product-service, 5xx, 3xx), a malformed or
 *       missing body, an unknown status string, a timeout, a refused connection, a failed token
 *       or exchange.</li>
 * </ul>
 *
 * <p>🔴 Fail-closed is the whole point (TASK-MONO-748 Failure Scenario 2 — «조회 장애 때 검증 없이
 * 저장»). There is no branch that answers a status it was not given.
 */
@Slf4j
public class HttpStoreSellerDirectory implements StoreSellerDirectory {

    /** The store's {@code SellerStatus} names (product-service, ADR-MONO-042). */
    static final Set<String> KNOWN_STORE_STATUSES =
            Set.of("PENDING_PROVISIONING", "ACTIVE", "SUSPENDED", "CLOSED");

    static final String SELLER_NOT_FOUND = "SELLER_NOT_FOUND";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient storeClient;
    private final StoreTenantTokenProvider tokenProvider;

    public HttpStoreSellerDirectory(RestClient storeClient, StoreTenantTokenProvider tokenProvider) {
        this.storeClient = storeClient;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Optional<String> findStatus(String sellerId) {
        String bearer;
        try {
            bearer = tokenProvider.bearer();
        } catch (RuntimeException e) {
            log.warn("store seller lookup: workload token unavailable (seller={}) → fail-closed: {}",
                    sellerId, e.toString());
            throw new StoreSellerLookupUnavailableException(
                    "could not obtain a store workload token; the link was not saved", e);
        }

        Outcome outcome;
        try {
            outcome = storeClient.get()
                    .uri("/internal/sellers/{sellerId}", sellerId)
                    .header("Authorization", "Bearer " + bearer)
                    .exchange((request, response) ->
                            new Outcome(response.getStatusCode(), readBody(response.getBody())));
        } catch (RuntimeException e) {
            log.warn("store seller lookup: transport failure (seller={}) → fail-closed: {}",
                    sellerId, e.toString());
            throw new StoreSellerLookupUnavailableException(
                    "store seller lookup failed; the link was not saved", e);
        }
        return interpret(sellerId, outcome);
    }

    private Optional<String> interpret(String sellerId, Outcome outcome) {
        int status = outcome.status().value();
        JsonNode body = outcome.body();

        if (status == 200) {
            String answeredId = text(body, "sellerId");
            String sellerStatus = text(body, "status");
            if (!sellerId.equals(answeredId) || sellerStatus == null
                    || !KNOWN_STORE_STATUSES.contains(sellerStatus)) {
                throw unavailable(sellerId, "unrecognised 200 body", outcome);
            }
            return Optional.of(sellerStatus);
        }
        if (status == 404 && SELLER_NOT_FOUND.equals(text(body, "code"))) {
            return Optional.empty();
        }
        throw unavailable(sellerId, "HTTP " + status, outcome);
    }

    private static StoreSellerLookupUnavailableException unavailable(String sellerId, String why,
                                                                     Outcome outcome) {
        log.warn("store seller lookup: no definite answer (seller={}, {}) → fail-closed", sellerId, why);
        return new StoreSellerLookupUnavailableException(
                "store gave no definite answer (" + why + "); the link was not saved");
    }

    private static JsonNode readBody(InputStream in) {
        try {
            byte[] bytes = in == null ? new byte[0] : in.readAllBytes();
            return bytes.length == 0 ? null : JSON.readTree(bytes);
        } catch (IOException | RuntimeException e) {
            return null; // a malformed body is «no definite answer», decided by interpret()
        }
    }

    private static String text(JsonNode body, String field) {
        if (body == null) {
            return null;
        }
        JsonNode n = body.get(field);
        return n != null && n.isTextual() ? n.asText() : null;
    }

    private record Outcome(HttpStatusCode status, JsonNode body) {
    }
}
