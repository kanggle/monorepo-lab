package com.wms.master.application.service;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateLotCommand;
import com.wms.master.application.command.DeactivateLotCommand;
import com.wms.master.application.command.ReactivateLotCommand;
import com.wms.master.application.command.UpdateLotCommand;
import com.wms.master.application.port.in.ExpireLotsBatchUseCase;
import com.wms.master.application.port.in.LotCrudUseCase;
import com.wms.master.application.port.in.LotQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.query.ListLotsQuery;
import com.wms.master.application.result.LotResult;
import com.wms.master.domain.event.LotCreatedEvent;
import com.wms.master.domain.event.LotDeactivatedEvent;
import com.wms.master.domain.event.LotReactivatedEvent;
import com.wms.master.domain.event.LotUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LotNotFoundException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lot application service. The aggregate-scoped transactional boundary
 * matches {@code SkuService} / {@code WarehouseService}: domain events are
 * written to the outbox inside the same transaction as the state change.
 *
 * <p>Lot is the only aggregate with cross-aggregate invariants beyond simple
 * FK. On create:
 * <ol>
 *   <li>Parent SKU must exist (404 {@code SKU_NOT_FOUND});
 *   <li>Parent SKU must be {@code ACTIVE} (422 {@code STATE_TRANSITION_INVALID});
 *   <li>Parent SKU must have {@code trackingType = LOT} (422
 *       {@code STATE_TRANSITION_INVALID}).
 * </ol>
 *
 * <p>Partner soft validation is deferred pending BE-005; the
 * {@code supplierPartnerId} is accepted as a free UUID and persisted verbatim.
 * When BE-005 lands with a Partner aggregate, the validation can be wired via
 * a {@code PartnerPersistencePort} without touching callers.
 *
 * <p>{@link #expireBatch(LocalDate)} runs the daily scheduled transition
 * {@code ACTIVE → EXPIRED}. Per the task spec §Scope.Application, the batch
 * is best-effort: a per-row failure is logged and does not abort the batch
 * so that one bad row does not block the others.
 */
@Service
@Transactional
public class LotService implements LotCrudUseCase, LotQueryUseCase, ExpireLotsBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(LotService.class);
    private static final String AGGREGATE_TYPE = "Lot";

    private final LotPersistencePort persistencePort;
    private final SkuPersistencePort skuPersistencePort;
    private final DomainEventPort eventPort;
    private final LotExpirationBatchProcessor expirationBatchProcessor;

    public LotService(LotPersistencePort persistencePort,
                      SkuPersistencePort skuPersistencePort,
                      DomainEventPort eventPort,
                      LotExpirationBatchProcessor expirationBatchProcessor) {
        this.persistencePort = persistencePort;
        this.skuPersistencePort = skuPersistencePort;
        this.eventPort = eventPort;
        this.expirationBatchProcessor = expirationBatchProcessor;
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public LotResult create(CreateLotCommand command) {
        // 1) Parent SKU must exist.
        Sku parent = skuPersistencePort.findById(command.skuId())
                .orElseThrow(() -> new SkuNotFoundException(command.skuId().toString()));

        // 2) Parent SKU must be ACTIVE. Going through Sku.isActive() insulates
        //    the application layer from the WarehouseStatus enum reuse on Sku
        //    (TASK-BE-018 item 1).
        if (!parent.isActive()) {
            throw new InvalidStateTransitionException(
                    "parent SKU " + parent.getId() + " is not ACTIVE");
        }

        // 3) Parent SKU must be LOT-tracked.
        if (parent.getTrackingType() != TrackingType.LOT) {
            throw new InvalidStateTransitionException(
                    "parent SKU " + parent.getId() + " is not LOT-tracked");
        }

        // 4) Partner soft validation is deferred to BE-005. supplierPartnerId
        //    accepted verbatim.

        Lot created = Lot.create(
                command.skuId(),
                command.lotNo(),
                command.manufacturedDate(),
                command.expiryDate(),
                command.supplierPartnerId(),
                command.actorId());

        Lot saved = persistencePort.insert(created);
        eventPort.publish(List.of(LotCreatedEvent.from(saved)));
        return LotResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public LotResult update(UpdateLotCommand command) {
        Lot loaded = loadOrThrow(command.id());

        // Immutable-field check fires BEFORE version check (mirrors SkuService):
        // a PATCH with a bad lotNo/skuId/manufacturedDate should surface as
        // IMMUTABLE_FIELD even if the version is wrong.
        loaded.rejectImmutableChange(
                command.skuIdAttempt(),
                command.lotNoAttempt(),
                command.manufacturedDateAttempt());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        List<String> changedFields = collectChangedFields(loaded, command);
        loaded.applyUpdate(
                command.expiryDate(),
                command.supplierPartnerId(),
                command.clearSupplierPartnerId(),
                command.actorId());

        Lot saved = saveWithOptimisticLock(loaded);

        if (!changedFields.isEmpty()) {
            eventPort.publish(List.of(LotUpdatedEvent.from(saved, changedFields)));
        }
        return LotResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public LotResult deactivate(DeactivateLotCommand command) {
        Lot loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        loaded.deactivate(command.actorId());
        Lot saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(LotDeactivatedEvent.from(saved, command.reason())));
        return LotResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public LotResult reactivate(ReactivateLotCommand command) {
        Lot loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        loaded.reactivate(command.actorId());
        Lot saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(LotReactivatedEvent.from(saved)));
        return LotResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public LotResult findById(UUID id) {
        return LotResult.from(loadOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PageResult<LotResult> list(ListLotsQuery query) {
        return persistencePort.findPage(query.criteria(), query.pageQuery())
                .map(LotResult::from);
    }

    /**
     * Scheduled expiration batch. Query the ACTIVE lots with
     * {@code expiry_date < today}, transition each to EXPIRED in its OWN new
     * transaction, and emit one {@code master.lot.expired} event per success
     * via the outbox.
     *
     * <p>Per-row failures are caught, logged, and counted so one bad row does
     * not block the others. Typical causes: transient DB contention, version
     * drift introduced by a concurrent user deactivating the lot between the
     * scheduler's query and the per-row transition.
     *
     * <p>Transaction boundary: the read-only candidate query runs outside any
     * enclosing transaction (this method is NOT annotated; the class-level
     * {@code @Transactional} default is overridden by the
     * {@link Transactional#propagation()} on {@link #execute(LocalDate)}
     * being {@code NOT_SUPPORTED}). Each row's transition then uses
     * {@link LotExpirationBatchProcessor#expireOne} which runs in a fresh
     * {@code REQUIRES_NEW} transaction so a mid-loop constraint violation
     * rolls back only that one row (TASK-BE-018 item 2).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LotExpirationResult execute(LocalDate today) {
        Instant scheduledAt = Instant.now();
        List<Lot> candidates = persistencePort.findAllByStatusAndExpiryDateBefore(
                LotStatus.ACTIVE, today);

        int expired = 0;
        int failed = 0;
        for (Lot candidate : candidates) {
            try {
                expirationBatchProcessor.expireOne(candidate, scheduledAt);
                expired++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Failed to expire lot id={} — {}",
                        candidate.getId(), ex.getMessage());
            }
        }

        log.info("Lot expiration batch: considered={} expired={} failed={} today={}",
                candidates.size(), expired, failed, today);
        return new LotExpirationResult(candidates.size(), expired, failed);
    }

    private Lot loadOrThrow(UUID id) {
        return persistencePort.findById(id)
                .orElseThrow(() -> new LotNotFoundException(id.toString()));
    }

    private void requireVersionMatch(UUID id, long expected, long actual) {
        if (expected != actual) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, id.toString(), expected, actual);
        }
    }

    private Lot saveWithOptimisticLock(Lot lot) {
        try {
            return persistencePort.update(lot);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, lot.getId().toString());
        }
    }

    private static List<String> collectChangedFields(Lot current, UpdateLotCommand cmd) {
        List<String> fields = new ArrayList<>();
        if (cmd.expiryDate() != null
                && !Objects.equals(cmd.expiryDate(), current.getExpiryDate())) {
            fields.add("expiryDate");
        }
        if (cmd.clearSupplierPartnerId()) {
            if (current.getSupplierPartnerId() != null) {
                fields.add("supplierPartnerId");
            }
        } else if (cmd.supplierPartnerId() != null
                && !Objects.equals(cmd.supplierPartnerId(), current.getSupplierPartnerId())) {
            fields.add("supplierPartnerId");
        }
        return fields;
    }
}
