package com.wms.master.application.service;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateLocationCommand;
import com.wms.master.application.command.DeactivateLocationCommand;
import com.wms.master.application.command.ReactivateLocationCommand;
import com.wms.master.application.command.UpdateLocationCommand;
import com.wms.master.application.port.in.LocationCrudUseCase;
import com.wms.master.application.port.in.LocationQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.LocationPersistencePort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListLocationsQuery;
import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.event.LocationCreatedEvent;
import com.wms.master.domain.event.LocationDeactivatedEvent;
import com.wms.master.domain.event.LocationReactivatedEvent;
import com.wms.master.domain.event.LocationUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LocationNotFoundException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.Location;
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
 * Location application service. Mirrors {@link WarehouseService} /
 * {@link ZoneService} layering: aggregate-scoped transactional boundary; domain
 * events published inside the same transaction as the state change (outbox
 * adapter handles the rest).
 *
 * <p>Location introduces two cross-aggregate concerns that Warehouse and Zone
 * did not:
 * <ul>
 *   <li><b>Dual parent check</b> — on create, the parent Zone must exist, must
 *       be ACTIVE, and its {@code warehouseId} must equal the command's
 *       {@code warehouseId}. Mismatch is surfaced as {@code ZoneNotFoundException}
 *       (leak-safe: we do not reveal that the zone exists under a different
 *       warehouse).
 *   <li><b>Parent warehouse code for prefix validation</b> — the Location
 *       domain factory needs the parent {@code warehouseCode} to enforce the
 *       {@code locationCode} prefix invariant. The service loads the parent
 *       warehouse for that reason.
 * </ul>
 */
@Service
@Transactional
public class LocationService implements LocationCrudUseCase, LocationQueryUseCase {

    private static final String AGGREGATE_TYPE = "Location";

    private final LocationPersistencePort locationPersistencePort;
    private final ZonePersistencePort zonePersistencePort;
    private final WarehousePersistencePort warehousePersistencePort;
    private final DomainEventPort eventPort;

    public LocationService(LocationPersistencePort locationPersistencePort,
                           ZonePersistencePort zonePersistencePort,
                           WarehousePersistencePort warehousePersistencePort,
                           DomainEventPort eventPort) {
        this.locationPersistencePort = locationPersistencePort;
        this.zonePersistencePort = zonePersistencePort;
        this.warehousePersistencePort = warehousePersistencePort;
        this.eventPort = eventPort;
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public LocationResult create(CreateLocationCommand command) {
        Zone parentZone = loadParentZoneMatchingWarehouse(command.zoneId(), command.warehouseId());
        if (!parentZone.isActive()) {
            throw new InvalidStateTransitionException("parent zone is not ACTIVE");
        }
        Warehouse parentWarehouse = requireWarehouseExists(command.warehouseId());

        Location created = Location.create(
                parentWarehouse.getWarehouseCode(),
                parentZone.getWarehouseId(),
                parentZone.getId(),
                command.locationCode(),
                command.aisle(),
                command.rack(),
                command.level(),
                command.bin(),
                command.locationType(),
                command.capacityUnits(),
                command.actorId());
        Location saved = locationPersistencePort.insert(created);
        eventPort.publish(List.of(LocationCreatedEvent.from(saved)));
        return LocationResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public LocationResult update(UpdateLocationCommand command) {
        Location loaded = loadOrThrow(command.id());
        // Reject immutable-field attempts BEFORE the version check so that a
        // PATCH with a bad locationCode / warehouseId / zoneId fails with
        // IMMUTABLE_FIELD (422) even if the version is correct.
        loaded.rejectImmutableChange(
                command.locationCodeAttempt(),
                command.warehouseIdAttempt(),
                command.zoneIdAttempt());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        List<String> changedFields = collectChangedFields(loaded, command);
        loaded.applyUpdate(
                command.locationType(),
                command.capacityUnits(),
                command.aisle(),
                command.rack(),
                command.level(),
                command.bin(),
                command.actorId());

        Location saved = saveWithOptimisticLock(loaded);

        if (!changedFields.isEmpty()) {
            eventPort.publish(List.of(LocationUpdatedEvent.from(saved, changedFields)));
        }
        return LocationResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public LocationResult deactivate(DeactivateLocationCommand command) {
        Location loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());

        // v1 local-only check — cross-service inventory check deferred to v2
        // (see architecture.md Open Items). The domain state transition is the
        // only guard here.
        loaded.deactivate(command.actorId());

        Location saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(LocationDeactivatedEvent.from(saved, command.reason())));
        return LocationResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public LocationResult reactivate(ReactivateLocationCommand command) {
        Location loaded = loadOrThrow(command.id());
        requireVersionMatch(command.id(), command.version(), loaded.getVersion());
        // Parent zone must still be ACTIVE at reactivate time.
        Zone parentZone = loadParentZoneMatchingWarehouse(loaded.getZoneId(), loaded.getWarehouseId());
        if (!parentZone.isActive()) {
            throw new InvalidStateTransitionException("parent zone is not ACTIVE");
        }

        loaded.reactivate(command.actorId());
        Location saved = saveWithOptimisticLock(loaded);
        eventPort.publish(List.of(LocationReactivatedEvent.from(saved)));
        return LocationResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public LocationResult findById(UUID id) {
        return LocationResult.from(loadOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PageResult<LocationResult> list(ListLocationsQuery query) {
        return locationPersistencePort.findPage(query.criteria(), query.pageQuery())
                .map(LocationResult::from);
    }

    private Location loadOrThrow(UUID id) {
        return locationPersistencePort.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id.toString()));
    }

    private void requireVersionMatch(UUID id, long expected, long actual) {
        if (expected != actual) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, id.toString(), expected, actual);
        }
    }

    private Location saveWithOptimisticLock(Location location) {
        try {
            return locationPersistencePort.update(location);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, location.getId().toString());
        }
    }

    private Warehouse requireWarehouseExists(UUID warehouseId) {
        return warehousePersistencePort.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseId.toString()));
    }

    /**
     * Load the parent zone and verify it belongs to the supplied warehouseId.
     * Mismatch is treated as "zone not found" — leak-safe. Absence is also
     * {@code ZoneNotFoundException}.
     */
    private Zone loadParentZoneMatchingWarehouse(UUID zoneId, UUID expectedWarehouseId) {
        Zone zone = zonePersistencePort.findById(zoneId)
                .orElseThrow(() -> new ZoneNotFoundException(zoneId.toString()));
        if (!zone.getWarehouseId().equals(expectedWarehouseId)) {
            throw new ZoneNotFoundException(zoneId.toString());
        }
        return zone;
    }

    private static List<String> collectChangedFields(Location current, UpdateLocationCommand cmd) {
        List<String> fields = new ArrayList<>(6);
        if (cmd.locationType() != null && !Objects.equals(cmd.locationType(), current.getLocationType())) {
            fields.add("locationType");
        }
        if (cmd.capacityUnits() != null && !Objects.equals(cmd.capacityUnits(), current.getCapacityUnits())) {
            fields.add("capacityUnits");
        }
        if (cmd.aisle() != null && !Objects.equals(cmd.aisle(), current.getAisle())) {
            fields.add("aisle");
        }
        if (cmd.rack() != null && !Objects.equals(cmd.rack(), current.getRack())) {
            fields.add("rack");
        }
        if (cmd.level() != null && !Objects.equals(cmd.level(), current.getLevel())) {
            fields.add("level");
        }
        if (cmd.bin() != null && !Objects.equals(cmd.bin(), current.getBin())) {
            fields.add("bin");
        }
        return fields;
    }
}
