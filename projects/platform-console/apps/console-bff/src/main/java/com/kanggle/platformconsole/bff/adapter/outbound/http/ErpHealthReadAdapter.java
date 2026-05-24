package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.usecase.DomainHealthCompositionUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ERP /actuator/health outbound leg for the Domain Health Overview composition.
 *
 * <p>Surfaces (§ 2.4.9.2 row 5):
 * <ul>
 *   <li>Endpoint: {@code GET /actuator/health} on the erp
 *       {@code masterdata-service} direct ({@code erp.local}; erp v1 has no
 *       gateway-service).</li>
 *   <li>Auth: <b>None</b> ({@code SecurityConfig} {@code permitAll} includes
 *       {@code /actuator/{health,info,prometheus}}).</li>
 *   <li>Tenant: NO header (not tenant-scoped).</li>
 * </ul>
 */
@Component
public class ErpHealthReadAdapter extends AbstractHealthReadAdapter
        implements DomainHealthCompositionUseCase.ErpHealthReadPort {

    public ErpHealthReadAdapter(@Qualifier("erpRestClient") RestClient client) {
        super(client);
    }
}
