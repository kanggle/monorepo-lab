package com.example.scmplatform.logistics.adapter.outbound.persistence;

import com.example.scmplatform.logistics.domain.model.Carrier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Local vendor-idempotency ground-truth (I4, ADR-052 §2.7). Keyed by {@code request_id =
 * shipment.id} — a repeat dispatch reads the cached {@code response_snapshot} and short-circuits
 * with <b>no network call</b>. The vendor a request went to is recorded, so a shipment cannot be
 * double-dispatched <b>across</b> vendors either.
 *
 * <p>Writes are {@code REQUIRES_NEW} (external-integrations.md §1.7): the dedupe row commits in
 * its own transaction so it survives a rollback of the surrounding dispatch transaction — the
 * fail-closed guarantee behind the WireMock replay test.
 *
 * <p>Public (used by the EasyPost adapter in a sibling package); its API is String-only, so no
 * JPA entity leaks out.
 */
@Component
public class DispatchDedupeStore {

    private final DispatchRequestDedupeJpaRepository repository;

    DispatchDedupeStore(DispatchRequestDedupeJpaRepository repository) {
        this.repository = repository;
    }

    /** Return the cached response snapshot for a request id, if a prior send recorded one. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<String> findSnapshot(UUID requestId) {
        return repository.findById(requestId)
                .map(DispatchRequestDedupeJpaEntity::getResponseSnapshot);
    }

    /**
     * Record the vendor ack snapshot for a request id. A concurrent duplicate insert (PK
     * collision) is swallowed — idempotent, the first writer's snapshot stands.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(UUID requestId, Carrier vendor, String responseSnapshot) {
        try {
            DispatchRequestDedupeJpaEntity entity = new DispatchRequestDedupeJpaEntity();
            entity.setRequestId(requestId);
            entity.setVendor(vendor.name());
            entity.setSentAt(Instant.now());
            entity.setResponseSnapshot(responseSnapshot);
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate — the row already exists; idempotent, ignore.
        }
    }
}
