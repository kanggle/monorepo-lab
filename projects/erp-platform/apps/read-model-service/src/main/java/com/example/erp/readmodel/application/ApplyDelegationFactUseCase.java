package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.command.DelegationFactCommand;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a single delegation event to the {@code delegation_fact_proj}
 * projection (E5 read-only — latest fact only, NOT history; the authoritative
 * grant state + audit history stay with {@code approval-service}). One
 * transaction per event: dedupe check → latest-state upsert keyed by
 * {@code grantId} → record provenance (TASK-ERP-BE-015).
 *
 * <p><b>Sticky-terminal REVOKED</b> + <b>out-of-order</b> tolerance are enforced
 * inside {@link DelegationFactProjection} (the domain object): {@code applyGrant}
 * never reverts a REVOKED row back to ACTIVE (last-event-wins with REVOKED
 * sticky); a {@code revoked} arriving before its {@code delegated} upserts a row
 * with the validity window ABSENT (no fabrication, E5 — the revoke payload carries
 * no window). This service holds NO delegation business logic (no state machine —
 * it projects the observed state). A duplicate {@code eventId} (already in
 * {@code processed_events}) is a no-op (T8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyDelegationFactUseCase {

    private final DelegationFactProjectionRepository delegationRepository;
    private final EventDedupeService dedupeService;

    @Transactional
    public void apply(DelegationFactCommand cmd) {
        if (dedupeService.isDuplicate(cmd.eventId())) {
            log.debug("Duplicate delegation event skipped: eventId={} topic={}",
                    cmd.eventId(), cmd.topic());
            return;
        }
        String id = cmd.grantId();
        DelegationFactProjection existing = delegationRepository.findById(id).orElse(null);

        if (cmd.isGranted()) {
            if (existing == null) {
                delegationRepository.save(DelegationFactProjection.ofGranted(
                        id, cmd.delegatorId(), cmd.delegateId(), cmd.validFrom(),
                        cmd.validTo(), cmd.reason(), cmd.occurredAt(), cmd.eventId()));
            } else {
                // Sticky-terminal: applyGrant is a status no-op on a REVOKED row.
                existing.applyGrant(cmd.delegatorId(), cmd.delegateId(), cmd.validFrom(),
                        cmd.validTo(), cmd.reason(), cmd.occurredAt(), cmd.eventId());
                delegationRepository.save(existing);
            }
        } else {
            // Revoke event.
            if (existing == null) {
                // Out-of-order: revoke before grant → validity window ABSENT (E5).
                delegationRepository.save(DelegationFactProjection.ofRevoked(
                        id, cmd.delegatorId(), cmd.delegateId(), cmd.reason(),
                        cmd.revokedAt(), cmd.occurredAt(), cmd.eventId()));
            } else {
                // Last-revoke-wins; never reverts to ACTIVE.
                existing.applyRevoke(cmd.delegatorId(), cmd.delegateId(), cmd.reason(),
                        cmd.revokedAt(), cmd.occurredAt(), cmd.eventId());
                delegationRepository.save(existing);
            }
        }
        dedupeService.markProcessed(cmd.eventId(), cmd.topic(), id);
    }
}
