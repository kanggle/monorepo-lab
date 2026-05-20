package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.usecase.DomainHealthCompositionUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * WMS /actuator/health outbound leg for the Domain Health Overview composition.
 *
 * <p>Surfaces (§ 2.4.9.2 row 2):
 * <ul>
 *   <li>Endpoint: {@code GET /actuator/health} on the WMS {@code gateway-service}
 *       primary entry ({@code wms.local}).</li>
 *   <li>Auth: <b>None</b> ({@code SecurityConfig.PUBLIC_PATHS} includes
 *       {@code /actuator/health} + {@code /actuator/health/**}).</li>
 *   <li>Tenant: NO header (not tenant-scoped).</li>
 * </ul>
 */
@Component
public class WmsHealthReadAdapter implements DomainHealthCompositionUseCase.WmsHealthReadPort {

    private final RestClient client;

    public WmsHealthReadAdapter(@Qualifier("wmsRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> read() {
        return (Map<String, Object>) client.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
