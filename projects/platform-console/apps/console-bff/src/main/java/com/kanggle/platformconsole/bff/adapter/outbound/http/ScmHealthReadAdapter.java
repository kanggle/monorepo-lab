package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.usecase.DomainHealthCompositionUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SCM /actuator/health outbound leg for the Domain Health Overview composition.
 *
 * <p>Surfaces (§ 2.4.9.2 row 3):
 * <ul>
 *   <li>Endpoint: {@code GET /actuator/health} on the SCM {@code gateway-service}
 *       primary entry ({@code scm.local}).</li>
 *   <li>Auth: <b>None</b> ({@code SecurityConfig.PUBLIC_PATHS} includes
 *       {@code /actuator/health}).</li>
 *   <li>Tenant: NO header (not tenant-scoped).</li>
 * </ul>
 */
@Component
public class ScmHealthReadAdapter extends AbstractHealthReadAdapter
        implements DomainHealthCompositionUseCase.ScmHealthReadPort {

    public ScmHealthReadAdapter(@Qualifier("scmRestClient") RestClient client) {
        super(client);
    }
}
