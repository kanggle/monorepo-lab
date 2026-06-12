package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.EcommerceHealthReadPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ecommerce /actuator/health outbound leg for the Domain Health Overview
 * composition (§ 2.4.9.2 row 6, TASK-MONO-241).
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /actuator/health} on the ecommerce
 *       {@code gateway-service} ({@code ecommerce.local}).</li>
 *   <li>Auth: <b>None</b> ({@code SecurityConfig.PUBLIC_PATHS} {@code permitAll}
 *       includes {@code /actuator/health} + {@code /actuator/health/**} +
 *       {@code /actuator/info} — WebFlux reactive gateway).</li>
 *   <li>Tenant: NO header (not tenant-scoped).</li>
 * </ul>
 */
@Component
public class EcommerceHealthReadAdapter extends AbstractHealthReadAdapter
        implements EcommerceHealthReadPort {

    public EcommerceHealthReadAdapter(@Qualifier("ecommerceRestClient") RestClient client) {
        super(client);
    }
}
