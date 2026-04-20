package com.wms.master.application.service;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateZoneCommand;
import com.wms.master.application.command.DeactivateZoneCommand;
import com.wms.master.application.command.ReactivateZoneCommand;
import com.wms.master.application.command.UpdateZoneCommand;
import com.wms.master.application.port.in.ZoneCrudUseCase;
import com.wms.master.application.port.in.ZoneQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListZonesQuery;
import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.event.ZoneCreatedEvent;
import com.wms.master.domain.event.ZoneDeactivatedEvent;
import com.wms.master.domain.event.ZoneReactivatedEvent;
import com.wms.master.domain.event.ZoneUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ReferenceIntegrityViolationException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zone application service. Mirrors {@link WarehouseService} layering:
 * aggregate-scoped transactional boundary; domain events published inside the
 * same transaction as the state change (outbox adapter handles the rest).
 *
 * <p>Cross-aggregate checks (parent Warehouse must exist and be ACTIVE) live
 * here at the application layer, not in the domain model — per the
 * task ticket's "no cross-aggregate JPA relationship" rule.
 */
@Service
@Transactional
public class ZoneService implements ZoneCrudUseCase, ZoneQueryUseCase {

    private static final String AGGREGATE_TYPE = "Zone";

    private final ZonePersistencePort zonePersistencePort;
    private final WarehousePersistencePort warehousePersistencePort;
    private final DomainEventPort eventPort;

    public ZoneService(ZonePersistencePort zonePersistencePort,
                       WarehousePersistencePort warehousePersistencePort,
                       DomainEventPort eventPort) {
        this.zonePersistencePort = zonePersistencePort;
        this.warehousePersistencePort = warehousePersistencePort;
        this.eventPort = eventPort;
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public ZoneResult create(CreateZoneCommand command) {
        requireActiveParentWarehouse(command.warehouseId());

        Zone created = Zone.create(
                command.warehouseId(),
                command.zoneCode(),
                command.name(),
                command.zoneType(),
                command.actorId());
        Zone saved = zonePersistencePort.insert(created);
        eventPort.publish(List.of(ZoneCreatedEvent.from(saved)));
        return ZoneResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public ZoneResult update(UpdateZoneCommand command) {
        Zone loaded = loadOrThrow(command.id());
        // Reject immutable-field attempts BEFORE the version check so that a
        // PATCH with a bad `zoneCode` fails with IMMUTABLE_FIELD (422) even if
        // the version is correct — tighter invariant, per contract §2.4.
        loaded.rejectImmutableChange(command.zoneCodeAttempt(), command.warehouseIdAttempt());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        List<String> changedFields = collectChangedFields(loaded, command);
        loaded.applyUpdate(command.name(), command.zoneType(), command.actorId());

        Zone saved = saveWithOptimisticLock(loaded);

        if (!changedFields.isEmpty()) {
            eventPort.publish(List.of(ZoneUpdatedEvent.from(saved, changedFields)));
        }
        return ZoneResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public ZoneResult deactivate(DeactivateZoneCommand command) {
        Zone loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        if (zonePersistencePort.hasActiveLocationsFor(loaded.getId())) {
            throw new ReferenceIntegrityViolationException(
                    AGGREGATE_TYPE, loaded.getId(), "zone has active locations");
        }
        loaded.deactivate(command.actorId());

        Zone saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(ZoneDeactivatedEvent.from(saved, command.reason())));
        return ZoneResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public ZoneResult reactivate(ReactivateZoneCommand command) {
        Zone loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());
        requireActiveParentWarehouse(loaded.getWarehouseId());

        loaded.reactivate(command.actorId());
        Zone saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(ZoneReactivatedEvent.from(saved)));
        return ZoneResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public ZoneResult findById(UUID id) {
        return ZoneResult.from(loadOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PageResult<ZoneResult> list(ListZonesQuery query) {
        // Parent must exist even for listing — a nested 404 is clearer than an
        // empty page when the warehouseId is wrong.
        requireWarehouseExists(query.criteria().warehouseId());
        return zonePersistencePort.findPage(query.criteria(), query.pageQuery())
                .map(ZoneResult::from);
    }

    private Zone loadOrThrow(UUID id) {
        return zonePersistencePort.findById(id)
                .orElseThrow(() -> new ZoneNotFoundException(id.toString()));
    }

    private void requireVersionMatch(UUID id, long expected, long actual) {
        if (expected != actual) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, id.toString(), expected, actual);
        }
    }

    private Zone saveWithOptimisticLock(Zone zone) {
        try {
            return zonePersistencePort.update(zone);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, zone.getId().toString());
        }
    }

    private Warehouse requireWarehouseExists(UUID warehouseId) {
        return warehousePersistencePort.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseId.toString()));
    }

    private void requireActiveParentWarehouse(UUID warehouseId) {
        Warehouse parent = requireWarehouseExists(warehouseId);
        if (!parent.isActive()) {
            throw new InvalidStateTransitionException("parent warehouse is not ACTIVE");
        }
    }

    private static List<String> collectChangedFields(Zone current, UpdateZoneCommand cmd) {
        List<String> fields = new ArrayList<>(2);
        if (cmd.name() != null && !Objects.equals(cmd.name(), current.getName())) {
            fields.add("name");
        }
        if (cmd.zoneType() != null && !Objects.equals(cmd.zoneType(), current.getZoneType())) {
            fields.add("zoneType");
        }
        return fields;
    }
}
