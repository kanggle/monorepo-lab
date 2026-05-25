package com.wms.outbound.adapter.out.tms.persistence;

import com.wms.outbound.application.port.out.TmsRequestDedupePort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of {@link TmsRequestDedupePort}.
 *
 * <p>Reads run with the ambient transaction context (or none — Postgres
 * auto-commit is sufficient for a single-row PK lookup). Writes open a
 * fresh {@link Propagation#REQUIRES_NEW} transaction because the caller
 * (the post-commit {@code TmsClientAdapter}) has no active TX after the
 * saga commit.
 *
 * <p>On PK conflict (concurrent re-fire of the same shipment) the second
 * writer is silently swallowed — the first row wins. The caller falls
 * back to {@link #findSnapshot(UUID)} on the next attempt.
 */
@Component("tmsRequestDedupePersistenceImpl")
public class TmsRequestDedupeRepositoryImpl implements TmsRequestDedupePort {

    private static final Logger log = LoggerFactory.getLogger(TmsRequestDedupeRepositoryImpl.class);

    private final TmsRequestDedupeJpaRepository repository;

    public TmsRequestDedupeRepositoryImpl(TmsRequestDedupeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Optional<String> findSnapshot(UUID requestId) {
        return repository.findById(requestId).map(TmsRequestDedupeEntity::getResponseSnapshot);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSnapshot(UUID requestId, Instant sentAt, String responseSnapshot) {
        try {
            repository.save(new TmsRequestDedupeEntity(requestId, sentAt, responseSnapshot));
        } catch (DataIntegrityViolationException duplicate) {
            // Concurrent re-fire — first writer wins. Safe to swallow.
            log.debug("tms_dedupe_pk_conflict requestId={} (concurrent insert)", requestId);
        }
    }
}
