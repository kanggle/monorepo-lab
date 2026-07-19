package com.wms.inbound.application.service;

import com.wms.inbound.application.command.CancelScmInboundExpectationCommand;
import com.wms.inbound.application.port.in.CancelScmInboundExpectationUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.domain.event.AsnCancelledEvent;
import com.wms.inbound.domain.model.Asn;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-MONO-050 D6.3 — cancel a wms inbound expectation on an scm PO-cancel event.
 *
 * <p>Marks the matching <em>open</em> ASN {@code CANCELLED} only while it is still cancellable
 * (not yet physically received — {@code CREATED}/{@code INSPECTING}). If the expectation was
 * already received (goods in putaway/closed) or never created, this is a <strong>no-op</strong>:
 * cancelling a received inbound would violate the physical invariant. Never routes to DLT.
 */
@Service
public class CancelScmInboundExpectationService implements CancelScmInboundExpectationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelScmInboundExpectationService.class);

    private static final String SCM_ACTOR = InboundRoles.SYSTEM_ACTOR_PREFIX + "scm-procurement";

    private final AsnPersistencePort asnPersistence;
    private final InboundEventPort eventPort;
    private final Clock clock;

    public CancelScmInboundExpectationService(AsnPersistencePort asnPersistence,
                                              InboundEventPort eventPort,
                                              Clock clock) {
        this.asnPersistence = asnPersistence;
        this.eventPort = eventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void cancel(CancelScmInboundExpectationCommand command) {
        String poNumber = command.poNumber();
        Optional<Asn> found = asnPersistence.findOpenByPoNumber(poNumber);
        if (found.isEmpty()) {
            log.info("scm_inbound_expected_cancel_noop poNumber={} — no open expectation (already received/closed or never created)",
                    poNumber);
            return;
        }

        Asn asn = found.get();
        if (!asn.isCancellable()) {
            log.info("scm_inbound_expected_cancel_noop poNumber={} asnId={} status={} — already received",
                    poNumber, asn.getId(), asn.getStatus());
            return;
        }

        String previousStatus = asn.getStatus().name();
        Instant now = clock.instant();
        String reason = command.reason() != null ? command.reason() : "SCM_PO_CANCELLED";
        asn.cancel(reason, now, SCM_ACTOR);
        Asn saved = asnPersistence.save(asn);

        eventPort.publish(new AsnCancelledEvent(
                saved.getId(), saved.getAsnNo(), previousStatus, reason, now, now, SCM_ACTOR));

        log.info("scm_inbound_expected_cancelled asnId={} poNumber={} previousStatus={}",
                saved.getId(), poNumber, previousStatus);
    }
}
