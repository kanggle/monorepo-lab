package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.WmsHealthReadPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
public class WmsHealthReadAdapter extends AbstractHealthReadAdapter
        implements WmsHealthReadPort {

    public WmsHealthReadAdapter(@Qualifier("wmsRestClient") RestClient client) {
        super(client);
    }
}
