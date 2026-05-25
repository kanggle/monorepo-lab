package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.GapHealthReadPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GAP /actuator/health outbound leg for the Domain Health Overview composition.
 *
 * <p>Surfaces (§ 2.4.9.2 row 1):
 * <ul>
 *   <li>Endpoint: {@code GET /actuator/health} on the GAP {@code gateway-service}
 *       primary entry (Traefik routes {@code gap.local} there).</li>
 *   <li>Auth: <b>None</b>. The producer's SecurityConfig
 *       ({@code gateway-service} {@code application.yml} {@code public-paths}
 *       includes {@code GET:/actuator/health}) marks this endpoint as
 *       {@code permitAll}. The leg sends NO {@code Authorization} header —
 *       ADR-MONO-017 § D4 governs § 2.4.5/6/7/8 data-API legs only.</li>
 *   <li>Tenant: NO {@code X-Tenant-Id} header — actuator endpoints are not
 *       tenant-scoped.</li>
 * </ul>
 */
@Component
public class GapHealthReadAdapter extends AbstractHealthReadAdapter
        implements GapHealthReadPort {

    public GapHealthReadAdapter(@Qualifier("gapRestClient") RestClient client) {
        super(client);
    }
}
