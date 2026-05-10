package com.wms.outbound.adapter.out.tms;

import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Standalone-profile fallback for {@link ShipmentNotificationPort}: logs the
 * call and returns a synthetic acknowledgement so local dev / smoke tests
 * (no TMS sandbox available) can exercise the full saga end-to-end.
 *
 * <p>The real Resilience4j-backed adapter ({@link TmsClientAdapter}) is
 * gated with {@code @Profile("!standalone")}; the two adapters are mutually
 * exclusive at runtime and Spring picks one based on the active profile
 * (standalone-profile/SKILL.md pattern).
 *
 * <p>This is the production stub used by the {@code application-standalone.yml}
 * configuration only — never wired in {@code dev} / {@code prod} profiles.
 */
@Component
@Profile("standalone")
public class StubTmsClientAdapter implements ShipmentNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(StubTmsClientAdapter.class);

    @Override
    public TmsAcknowledgement notify(UUID shipmentId) {
        String requestId = UUID.randomUUID().toString();
        log.info("TMS notify (standalone stub): shipmentId={} requestId={}", shipmentId, requestId);
        return TmsAcknowledgement.success(requestId);
    }
}
