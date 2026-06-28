package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.ErpNotificationsReadPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * erp notification-inbox outbound leg for the console notification aggregator
 * (ADR-MONO-043 P3a / D2). Reuses the existing {@code erpRestClient} bean
 * ({@code http://erp.local}, 2s per-leg timeout — see {@code RestClientConfig}).
 *
 * <p>Surfaces (contract § 2):
 * <ul>
 *   <li>{@code GET /api/erp/notifications?unread&page&size} — the caller's inbox</li>
 *   <li>{@code POST /api/erp/notifications/{id}/read} — idempotent mark-read</li>
 * </ul>
 *
 * <p><b>erp-no-X-Tenant-Id divergence (D6 / contract § 3)</b>: this adapter
 * deliberately does <b>NOT</b> use {@link RestClientHelper#authenticatedGet}
 * (which always sets {@code X-Tenant-Id} for the Operator Overview legs). erp
 * resolves tenant + recipient from the JWT ({@code tenant_id} / {@code sub}
 * claims) and rejects callers that supply a conflicting {@code X-Tenant-Id}.
 * The {@link RestClient} is therefore driven directly, attaching only
 * {@code Authorization: Bearer <credential>} + {@code Accept: application/json}.
 * The credential is the GAP/IAM OIDC access token ({@code IamOidcAccessToken}) —
 * the aggregator is a dispatcher, never a credential rewrite (D6).
 */
@Component
public class ErpNotificationsReadAdapter implements ErpNotificationsReadPort {

    private final RestClient client;

    public ErpNotificationsReadAdapter(@Qualifier("erpRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> readInbox(String credential, int page, int size, Boolean unread) {
        return (Map<String, Object>) client.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/erp/notifications")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (unread != null) {
                        uriBuilder.queryParam("unread", unread);
                    }
                    return uriBuilder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                // NO X-Tenant-Id — erp resolves tenant from the JWT tenant_id claim (D6 / contract § 3).
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> markRead(String credential, String id) {
        return (Map<String, Object>) client.post()
                .uri("/api/erp/notifications/{id}/read", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                // NO X-Tenant-Id — see readInbox (D6 / contract § 3).
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
