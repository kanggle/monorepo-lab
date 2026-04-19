package com.wms.master.application.service;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateWarehouseCommand;
import com.wms.master.application.command.DeactivateWarehouseCommand;
import com.wms.master.application.command.ReactivateWarehouseCommand;
import com.wms.master.application.command.UpdateWarehouseCommand;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseDeactivatedEvent;
import com.wms.master.domain.event.WarehouseReactivatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.model.Warehouse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WarehouseService implements WarehouseCrudUseCase, WarehouseQueryUseCase {

    private static final String AGGREGATE_TYPE = "Warehouse";

    private final WarehousePersistencePort persistencePort;
    private final DomainEventPort eventPort;

    public WarehouseService(WarehousePersistencePort persistencePort,
                            DomainEventPort eventPort) {
        this.persistencePort = persistencePort;
        this.eventPort = eventPort;
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public WarehouseResult create(CreateWarehouseCommand command) {
        Warehouse created = Warehouse.create(
                command.warehouseCode(),
                command.name(),
                command.address(),
                command.timezone(),
                command.actorId());
        Warehouse saved = persistencePort.insert(created);
        eventPort.publish(List.of(WarehouseCreatedEvent.from(saved)));
        return WarehouseResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public WarehouseResult update(UpdateWarehouseCommand command) {
        Warehouse loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        List<String> changedFields = collectChangedFields(loaded, command);
        loaded.applyUpdate(
                command.name(),
                command.address(),
                command.timezone(),
                command.actorId());

        Warehouse saved = saveWithOptimisticLock(loaded);

        if (!changedFields.isEmpty()) {
            eventPort.publish(List.of(WarehouseUpdatedEvent.from(saved, changedFields)));
        }
        return WarehouseResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public WarehouseResult deactivate(DeactivateWarehouseCommand command) {
        Warehouse loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        loaded.deactivate(command.actorId());
        // v1 reference-integrity check is local-only. Warehouse has no local child
        // aggregates yet (Zone ships in a later task); when it does, check here
        // and raise REFERENCE_INTEGRITY_VIOLATION before calling the port.

        Warehouse saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(WarehouseDeactivatedEvent.from(saved, command.reason())));
        return WarehouseResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public WarehouseResult reactivate(ReactivateWarehouseCommand command) {
        Warehouse loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        loaded.reactivate(command.actorId());
        Warehouse saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(WarehouseReactivatedEvent.from(saved)));
        return WarehouseResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public WarehouseResult findById(UUID id) {
        return WarehouseResult.from(loadOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public WarehouseResult findByCode(String warehouseCode) {
        Warehouse warehouse = persistencePort.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        return WarehouseResult.from(warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PageResult<WarehouseResult> list(ListWarehousesQuery query) {
        return persistencePort.findPage(query.criteria(), query.pageQuery())
                .map(WarehouseResult::from);
    }

    private Warehouse loadOrThrow(UUID id) {
        return persistencePort.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id.toString()));
    }

    private void requireVersionMatch(UUID id, long expected, long actual) {
        if (expected != actual) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, id.toString(), expected, actual);
        }
    }

    private Warehouse saveWithOptimisticLock(Warehouse warehouse) {
        try {
            return persistencePort.update(warehouse);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, warehouse.getId().toString());
        }
    }

    private static List<String> collectChangedFields(Warehouse current, UpdateWarehouseCommand cmd) {
        List<String> fields = new ArrayList<>(3);
        if (cmd.name() != null && !Objects.equals(cmd.name(), current.getName())) {
            fields.add("name");
        }
        if (cmd.address() != null && !Objects.equals(cmd.address(), current.getAddress())) {
            fields.add("address");
        }
        if (cmd.timezone() != null && !Objects.equals(cmd.timezone(), current.getTimezone())) {
            fields.add("timezone");
        }
        return fields;
    }
}
