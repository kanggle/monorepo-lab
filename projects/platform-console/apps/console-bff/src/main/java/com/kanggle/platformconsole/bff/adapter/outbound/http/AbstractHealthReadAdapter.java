package com.kanggle.platformconsole.bff.adapter.outbound.http;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Base class for the 5 domain {@code /actuator/health} outbound legs.
 *
 * <p>All five domain health adapters ({@link GapHealthReadAdapter},
 * {@link WmsHealthReadAdapter}, {@link ScmHealthReadAdapter},
 * {@link FinanceHealthReadAdapter}, {@link ErpHealthReadAdapter}) share
 * identical logic: {@code GET /actuator/health} with {@code Accept:
 * application/json} and no auth / tenant headers (actuator endpoints are
 * {@code permitAll} and not tenant-scoped). The only variation is the
 * injected {@link RestClient} bean (one per domain, keyed by qualifier).
 *
 * <p>This abstract base eliminates the duplication. Each concrete sub-class
 * holds only a constructor that delegates to {@link #AbstractHealthReadAdapter(RestClient)}.
 * Spring component-scanning targets the concrete classes only
 * (abstract classes are never scanned as beans — Edge Case §99).
 */
public abstract class AbstractHealthReadAdapter {

    private final RestClient client;

    protected AbstractHealthReadAdapter(RestClient client) {
        this.client = client;
    }

    /**
     * Reads {@code GET /actuator/health} from the domain this adapter serves.
     *
     * <p>No {@code Authorization} or {@code X-Tenant-Id} header is sent —
     * actuator health endpoints are {@code permitAll} and not tenant-scoped
     * (ADR-MONO-017 § D4 governs the § 2.4.5/6/7/8 data-API legs only).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> read() {
        return (Map<String, Object>) client.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
