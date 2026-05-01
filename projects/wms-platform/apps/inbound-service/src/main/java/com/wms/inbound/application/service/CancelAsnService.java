package com.wms.inbound.application.service;

import com.wms.inbound.application.command.CancelAsnCommand;
import com.wms.inbound.application.port.in.CancelAsnUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.domain.event.AsnCancelledEvent;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.model.Asn;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelAsnService implements CancelAsnUseCase {

    private static final String ROLE_INBOUND_ADMIN = "ROLE_INBOUND_ADMIN";
    private static final Logger log = LoggerFactory.getLogger(CancelAsnService.class);

    private final AsnPersistencePort asnPersistence;
    private final InboundEventPort eventPort;
    private final Clock clock;

    public CancelAsnService(AsnPersistencePort asnPersistence,
                            InboundEventPort eventPort,
                            Clock clock) {
        this.asnPersistence = asnPersistence;
        this.eventPort = eventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AsnResult cancel(CancelAsnCommand command) {
        requireRole(command.callerRoles(), ROLE_INBOUND_ADMIN);

        Asn asn = asnPersistence.findById(command.asnId())
                .orElseThrow(() -> new AsnNotFoundException(command.asnId()));

        String previousStatus = asn.getStatus().name();
        Instant now = clock.instant();
        asn.cancel(command.reason(), now, command.actorId());
        Asn saved = asnPersistence.save(asn);

        AsnCancelledEvent event = new AsnCancelledEvent(
                saved.getId(), saved.getAsnNo(), previousStatus,
                command.reason(), now, now, command.actorId());
        eventPort.publish(event);

        log.info("asn_cancelled asnId={} previousStatus={}", saved.getId(), previousStatus);
        return ReceiveAsnService.toResult(saved);
    }

    private static void requireRole(java.util.Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }
}
