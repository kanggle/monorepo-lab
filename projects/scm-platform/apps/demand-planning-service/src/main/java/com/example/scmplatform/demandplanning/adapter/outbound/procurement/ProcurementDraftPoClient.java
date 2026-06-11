package com.example.scmplatform.demandplanning.adapter.outbound.procurement;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.scmplatform.demandplanning.application.port.outbound.ProcurementDraftPoPort;
import com.example.scmplatform.demandplanning.domain.error.ProcurementUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Synchronous intra-scm REST client for the procurement DRAFT-PO leg
 * (ADR-MONO-027 D5). Posts to {@code POST /api/procurement/po/from-suggestion}.
 *
 * <p>Intra-scm trust: the operator's bearer token is propagated unchanged
 * (v1 — no workload-identity infra in scm yet; procurement accepts
 * {@code tenant_id ∈ {scm,*}} or signed {@code entitled_domains ∋ scm}). The
 * call is idempotent on {@code sourceSuggestionId}, so a retry after a lost
 * response yields the same PO, never a duplicate.
 *
 * <p>Any transport failure or non-2xx response is surfaced as
 * {@link ProcurementUnavailableException} so the approve use case leaves the
 * suggestion {@code APPROVED} for operator retry.
 */
@Slf4j
@Component
public class ProcurementDraftPoClient implements ProcurementDraftPoPort {

    private static final String FROM_SUGGESTION_PATH = "/api/procurement/po/from-suggestion";
    private static final String ORIGIN_DEMAND_PLANNING = "DEMAND_PLANNING";
    private static final String UNIT_PRICE_REF = "LAST_KNOWN";

    private final RestClient client;

    public ProcurementDraftPoClient(
            @Value("${scmplatform.demand-planning.procurement.base-url}") String baseUrl,
            @Value("${scmplatform.demand-planning.procurement.connect-timeout-ms:2000}") int connectMs,
            @Value("${scmplatform.demand-planning.procurement.read-timeout-ms:10000}") int readMs) {
        this.client = ResilienceClientFactory.buildRestClient(baseUrl, connectMs, readMs);
    }

    @Override
    public DraftPoResult createDraftFromSuggestion(DraftPoCommand cmd, String bearerToken) {
        Map<String, Object> body = Map.of(
                "supplierId", cmd.supplierId().toString(),
                "currency", cmd.currency(),
                "origin", ORIGIN_DEMAND_PLANNING,
                "sourceSuggestionId", cmd.sourceSuggestionId().toString(),
                "lines", List.of(Map.of(
                        "lineNo", 1,
                        "sku", cmd.skuCode(),
                        "quantity", cmd.quantity(),
                        "unitPriceRef", UNIT_PRICE_REF))
        );

        JsonNode response;
        try {
            response = client.post()
                    .uri(FROM_SUGGESTION_PATH)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .headers(h -> {
                        if (bearerToken != null && !bearerToken.isBlank()) {
                            h.set(HttpHeaders.AUTHORIZATION, bearerToken);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("procurement from-suggestion call failed for suggestion={}: {}",
                    cmd.sourceSuggestionId(), e.getMessage());
            throw new ProcurementUnavailableException(
                    "procurement DRAFT-PO call failed: " + e.getMessage(), e);
        }

        JsonNode data = response == null ? null : response.path("data");
        String poId = data == null ? null : data.path("id").asText(null);
        String poStatus = data == null ? null : data.path("status").asText(null);
        if (poId == null || poId.isBlank()) {
            throw new ProcurementUnavailableException(
                    "procurement returned no PO id for suggestion=" + cmd.sourceSuggestionId());
        }
        log.info("procurement DRAFT PO created/fetched for suggestion={}: poId={} status={}",
                cmd.sourceSuggestionId(), poId, poStatus);
        return new DraftPoResult(poId, poStatus);
    }
}
