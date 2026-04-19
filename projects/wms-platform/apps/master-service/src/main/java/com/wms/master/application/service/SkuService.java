package com.wms.master.application.service;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateSkuCommand;
import com.wms.master.application.command.DeactivateSkuCommand;
import com.wms.master.application.command.ReactivateSkuCommand;
import com.wms.master.application.command.UpdateSkuCommand;
import com.wms.master.application.port.in.SkuCrudUseCase;
import com.wms.master.application.port.in.SkuQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.query.ListSkusQuery;
import com.wms.master.application.result.SkuResult;
import com.wms.master.domain.event.SkuCreatedEvent;
import com.wms.master.domain.event.SkuDeactivatedEvent;
import com.wms.master.domain.event.SkuReactivatedEvent;
import com.wms.master.domain.event.SkuUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.model.Sku;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SKU application service. Mirrors {@code WarehouseService} layering:
 * aggregate-scoped transactional boundary; domain events published inside the
 * same transaction as the state change (outbox adapter handles the rest).
 *
 * <p>SKU has no parent aggregate reference, so create/reactivate do not perform
 * the parent-active check Zone and Location require. It does carry a
 * {@link SkuPersistencePort#hasActiveLotsFor(UUID)} guard on deactivate —
 * stubbed in v1, fully wired when TASK-BE-006 (Lot) lands.
 */
@Service
@Transactional
public class SkuService implements SkuCrudUseCase, SkuQueryUseCase {

    private static final String AGGREGATE_TYPE = "Sku";

    private final SkuPersistencePort persistencePort;
    private final DomainEventPort eventPort;

    public SkuService(SkuPersistencePort persistencePort,
                      DomainEventPort eventPort) {
        this.persistencePort = persistencePort;
        this.eventPort = eventPort;
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public SkuResult create(CreateSkuCommand command) {
        Sku created = Sku.create(
                command.skuCode(),
                command.name(),
                command.description(),
                command.barcode(),
                command.baseUom(),
                command.trackingType(),
                command.weightGrams(),
                command.volumeMl(),
                command.hazardClass(),
                command.shelfLifeDays(),
                command.actorId());
        Sku saved = persistencePort.insert(created);
        eventPort.publish(List.of(SkuCreatedEvent.from(saved)));
        return SkuResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public SkuResult update(UpdateSkuCommand command) {
        Sku loaded = loadOrThrow(command.id());
        // Reject immutable-field attempts BEFORE the version check so that a
        // PATCH with a bad skuCode/baseUom/trackingType fails with
        // IMMUTABLE_FIELD (422) even if the version is correct.
        loaded.rejectImmutableChange(
                command.skuCodeAttempt(),
                command.baseUomAttempt(),
                command.trackingTypeAttempt());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        List<String> changedFields = collectChangedFields(loaded, command);
        loaded.applyUpdate(
                command.name(),
                command.description(),
                command.barcode(),
                command.weightGrams(),
                command.volumeMl(),
                command.hazardClass(),
                command.shelfLifeDays(),
                command.actorId());

        Sku saved = saveWithOptimisticLock(loaded);

        if (!changedFields.isEmpty()) {
            eventPort.publish(List.of(SkuUpdatedEvent.from(saved, changedFields)));
        }
        return SkuResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public SkuResult deactivate(DeactivateSkuCommand command) {
        Sku loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        if (persistencePort.hasActiveLotsFor(loaded.getId())) {
            throw new InvalidStateTransitionException("sku has active lots");
        }
        loaded.deactivate(command.actorId());

        Sku saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(SkuDeactivatedEvent.from(saved, command.reason())));
        return SkuResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public SkuResult reactivate(ReactivateSkuCommand command) {
        Sku loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        loaded.reactivate(command.actorId());
        Sku saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(SkuReactivatedEvent.from(saved)));
        return SkuResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public SkuResult findById(UUID id) {
        return SkuResult.from(loadOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public SkuResult findBySkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new SkuNotFoundException("<blank>");
        }
        String normalized = skuCode.strip().toUpperCase(Locale.ROOT);
        Sku sku = persistencePort.findBySkuCode(normalized)
                .orElseThrow(() -> new SkuNotFoundException(skuCode));
        return SkuResult.from(sku);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public SkuResult findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new SkuNotFoundException("<blank>");
        }
        Sku sku = persistencePort.findByBarcode(barcode)
                .orElseThrow(() -> new SkuNotFoundException(barcode));
        return SkuResult.from(sku);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PageResult<SkuResult> list(ListSkusQuery query) {
        return persistencePort.findPage(query.criteria(), query.pageQuery())
                .map(SkuResult::from);
    }

    private Sku loadOrThrow(UUID id) {
        return persistencePort.findById(id)
                .orElseThrow(() -> new SkuNotFoundException(id.toString()));
    }

    private void requireVersionMatch(UUID id, long expected, long actual) {
        if (expected != actual) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, id.toString(), expected, actual);
        }
    }

    private Sku saveWithOptimisticLock(Sku sku) {
        try {
            return persistencePort.update(sku);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, sku.getId().toString());
        }
    }

    private static List<String> collectChangedFields(Sku current, UpdateSkuCommand cmd) {
        List<String> fields = new ArrayList<>();
        if (cmd.name() != null && !Objects.equals(cmd.name(), current.getName())) {
            fields.add("name");
        }
        if (cmd.description() != null && !Objects.equals(cmd.description(), current.getDescription())) {
            fields.add("description");
        }
        if (cmd.barcode() != null && !Objects.equals(cmd.barcode(), current.getBarcode())) {
            fields.add("barcode");
        }
        if (cmd.weightGrams() != null && !Objects.equals(cmd.weightGrams(), current.getWeightGrams())) {
            fields.add("weightGrams");
        }
        if (cmd.volumeMl() != null && !Objects.equals(cmd.volumeMl(), current.getVolumeMl())) {
            fields.add("volumeMl");
        }
        if (cmd.hazardClass() != null && !Objects.equals(cmd.hazardClass(), current.getHazardClass())) {
            fields.add("hazardClass");
        }
        if (cmd.shelfLifeDays() != null
                && !Objects.equals(cmd.shelfLifeDays(), current.getShelfLifeDays())) {
            fields.add("shelfLifeDays");
        }
        return fields;
    }
}
