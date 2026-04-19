package com.wms.master.domain.model;

import com.example.common.id.UuidV7;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Zone aggregate root. A zone is the second-level grouping under a warehouse
 * and classifies locations by environmental or functional role (ambient,
 * chilled, returns, etc.). See {@code specs/services/master-service/domain-model.md}
 * §2 for the authoritative invariants.
 *
 * <p>Framework-free POJO. JPA/Spring annotations live on the adapter-side
 * {@code ZoneJpaEntity}.
 *
 * <p>Reuses {@link WarehouseStatus} as the status type (ACTIVE/INACTIVE is
 * identical for both aggregates in v1). Extracting a shared {@code EntityStatus}
 * is a deferred refactor — see TASK-BE-002 Implementation Notes.
 */
public final class Zone {

    private static final Pattern CODE_PATTERN = Pattern.compile("^Z-[A-Z0-9]+$");
    private static final int NAME_MAX_LENGTH = 100;

    private UUID id;
    private UUID warehouseId;
    private String zoneCode;
    private String name;
    private ZoneType zoneType;
    private WarehouseStatus status;
    private long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Zone() {}

    public static Zone create(
            UUID warehouseId,
            String zoneCode,
            String name,
            ZoneType zoneType,
            String actorId) {
        validateWarehouseId(warehouseId);
        validateCode(zoneCode);
        validateName(name);
        validateZoneType(zoneType);
        validateActor(actorId);

        Instant now = Instant.now();
        Zone zone = new Zone();
        zone.id = UuidV7.randomUuid();
        zone.warehouseId = warehouseId;
        zone.zoneCode = zoneCode;
        zone.name = name;
        zone.zoneType = zoneType;
        zone.status = WarehouseStatus.ACTIVE;
        zone.version = 0L;
        zone.createdAt = now;
        zone.createdBy = actorId;
        zone.updatedAt = now;
        zone.updatedBy = actorId;
        return zone;
    }

    public static Zone reconstitute(
            UUID id,
            UUID warehouseId,
            String zoneCode,
            String name,
            ZoneType zoneType,
            WarehouseStatus status,
            long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        Zone zone = new Zone();
        zone.id = id;
        zone.warehouseId = warehouseId;
        zone.zoneCode = zoneCode;
        zone.name = name;
        zone.zoneType = zoneType;
        zone.status = status;
        zone.version = version;
        zone.createdAt = createdAt;
        zone.createdBy = createdBy;
        zone.updatedAt = updatedAt;
        zone.updatedBy = updatedBy;
        return zone;
    }

    /**
     * Apply a partial mutation. Only {@code name} and {@code zoneType} are
     * mutable; null arguments mean "no change".
     */
    public void applyUpdate(String newName, ZoneType newZoneType, String actorId) {
        validateActor(actorId);
        if (newName != null) {
            validateName(newName);
            this.name = newName;
        }
        if (newZoneType != null) {
            this.zoneType = newZoneType;
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * Reject client attempts to change immutable fields. Called before
     * {@link #applyUpdate} by the application layer when a raw PATCH body carries
     * any of the immutable field names.
     */
    public void rejectImmutableChange(String zoneCodeAttempt, UUID warehouseIdAttempt) {
        if (zoneCodeAttempt != null && !zoneCodeAttempt.equals(this.zoneCode)) {
            throw new ImmutableFieldException("zoneCode");
        }
        if (warehouseIdAttempt != null && !warehouseIdAttempt.equals(this.warehouseId)) {
            throw new ImmutableFieldException("warehouseId");
        }
    }

    public void deactivate(String actorId) {
        validateActor(actorId);
        if (this.status != WarehouseStatus.ACTIVE) {
            throw new InvalidStateTransitionException(this.status.name(), "deactivate");
        }
        this.status = WarehouseStatus.INACTIVE;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public void reactivate(String actorId) {
        validateActor(actorId);
        if (this.status != WarehouseStatus.INACTIVE) {
            throw new InvalidStateTransitionException(this.status.name(), "reactivate");
        }
        this.status = WarehouseStatus.ACTIVE;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public boolean isActive() {
        return this.status == WarehouseStatus.ACTIVE;
    }

    private static void validateWarehouseId(UUID warehouseId) {
        if (warehouseId == null) {
            throw new ValidationException("warehouseId is required");
        }
    }

    private static void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("zoneCode is required");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ValidationException("zoneCode must match pattern ^Z-[A-Z0-9]+$");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("name must be at most " + NAME_MAX_LENGTH + " characters");
        }
    }

    private static void validateZoneType(ZoneType zoneType) {
        if (zoneType == null) {
            throw new ValidationException("zoneType is required");
        }
    }

    private static void validateActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ValidationException("actorId is required");
        }
    }

    public UUID getId() { return id; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getZoneCode() { return zoneCode; }
    public String getName() { return name; }
    public ZoneType getZoneType() { return zoneType; }
    public WarehouseStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Zone other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
