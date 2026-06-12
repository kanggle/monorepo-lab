package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: ecommerce {@code gateway-service}
 * {@code /actuator/health} read for the Domain Health composition
 * (§ 2.4.9.2 row 6, TASK-MONO-241).
 *
 * <p>Mirrors {@link ErpHealthReadPort} — the leg is credential-less (public
 * actuator, outside ADR-MONO-017 § D4 scope). This is the one health leg that
 * is NOT also an Operator Overview (§ 2.4.9.1) leg; the ecommerce operator-plane
 * snapshot is a deferred follow-up (ADR-MONO-030 Step 4 facet a-후속-2).
 */
public interface EcommerceHealthReadPort {
    Map<String, Object> read();
}
