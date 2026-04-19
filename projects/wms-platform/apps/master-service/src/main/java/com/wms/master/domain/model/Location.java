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
 * Location aggregate root. A location is the third-level placement under
 * warehouse + zone and denotes an addressable storage position
 * (aisle/rack/level/bin) where inventory lives. See
 * {@code specs/services/master-service/domain-model.md} §3 for the
 * authoritative invariants.
 *
 * <p>Framework-free POJO. JPA/Spring annotations live on the adapter-side
 * {@code LocationJpaEntity}.
 *
 * <p>Reuses {@link WarehouseStatus} as the status type — ACTIVE / INACTIVE
 * mirrors Warehouse and Zone.
 *
 * <p>Dual-parent invariant: every location carries both {@code warehouseId} and
 * {@code zoneId}. The application layer enforces that the parent zone belongs
 * to the same warehouse — this domain class trusts what it is handed.
 */
public final class Location {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^WH\\d{2,3}-[A-Z0-9]+(-[A-Z0-9]+){1,5}$");
    private static final Pattern HIERARCHY_COMPONENT_PATTERN =
            Pattern.compile("^[A-Z0-9]+$");
    private static final int LOCATION_CODE_MAX_LENGTH = 40;
    private static final int HIERARCHY_FIELD_MAX_LENGTH = 10;

    private UUID id;
    private UUID warehouseId;
    private UUID zoneId;
    private String locationCode;
    private String aisle;
    private String rack;
    private String level;
    private String bin;
    private LocationType locationType;
    private Integer capacityUnits;
    private WarehouseStatus status;
    private long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Location() {}

    /**
     * Create a new Location. The {@code warehouseCode} is the parent warehouse's
     * business code (e.g. {@code WH01}) used to enforce the {@code locationCode}
     * prefix invariant. The application layer must load the parent warehouse and
     * pass its code here; the domain does not cross aggregates.
     */
    public static Location create(
            String warehouseCode,
            UUID warehouseId,
            UUID zoneId,
            String locationCode,
            String aisle,
            String rack,
            String level,
            String bin,
            LocationType locationType,
            Integer capacityUnits,
            String actorId) {
        validateWarehouseCode(warehouseCode);
        validateWarehouseId(warehouseId);
        validateZoneId(zoneId);
        validateLocationCode(locationCode, warehouseCode);
        validateHierarchyField("aisle", aisle);
        validateHierarchyField("rack", rack);
        validateHierarchyField("level", level);
        validateHierarchyField("bin", bin);
        validateLocationType(locationType);
        validateCapacityUnits(capacityUnits);
        validateActor(actorId);

        Instant now = Instant.now();
        Location loc = new Location();
        loc.id = UuidV7.randomUuid();
        loc.warehouseId = warehouseId;
        loc.zoneId = zoneId;
        loc.locationCode = locationCode;
        loc.aisle = aisle;
        loc.rack = rack;
        loc.level = level;
        loc.bin = bin;
        loc.locationType = locationType;
        loc.capacityUnits = capacityUnits;
        loc.status = WarehouseStatus.ACTIVE;
        loc.version = 0L;
        loc.createdAt = now;
        loc.createdBy = actorId;
        loc.updatedAt = now;
        loc.updatedBy = actorId;
        return loc;
    }

    public static Location reconstitute(
            UUID id,
            UUID warehouseId,
            UUID zoneId,
            String locationCode,
            String aisle,
            String rack,
            String level,
            String bin,
            LocationType locationType,
            Integer capacityUnits,
            WarehouseStatus status,
            long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        Location loc = new Location();
        loc.id = id;
        loc.warehouseId = warehouseId;
        loc.zoneId = zoneId;
        loc.locationCode = locationCode;
        loc.aisle = aisle;
        loc.rack = rack;
        loc.level = level;
        loc.bin = bin;
        loc.locationType = locationType;
        loc.capacityUnits = capacityUnits;
        loc.status = status;
        loc.version = version;
        loc.createdAt = createdAt;
        loc.createdBy = createdBy;
        loc.updatedAt = updatedAt;
        loc.updatedBy = updatedBy;
        return loc;
    }

    /**
     * Apply a partial mutation. Only {@code locationType}, {@code capacityUnits},
     * and hierarchy fields are mutable; null arguments mean "no change". Hierarchy
     * fields additionally accept a non-null {@code ""} to clear — but because we
     * use null for "no change", explicit clearing is not supported in v1 (caller
     * can only set a non-empty value). Matches Zone's convention.
     */
    public void applyUpdate(
            LocationType newLocationType,
            Integer newCapacityUnits,
            String newAisle,
            String newRack,
            String newLevel,
            String newBin,
            String actorId) {
        validateActor(actorId);
        if (newLocationType != null) {
            this.locationType = newLocationType;
        }
        if (newCapacityUnits != null) {
            validateCapacityUnits(newCapacityUnits);
            this.capacityUnits = newCapacityUnits;
        }
        if (newAisle != null) {
            validateHierarchyField("aisle", newAisle);
            this.aisle = newAisle;
        }
        if (newRack != null) {
            validateHierarchyField("rack", newRack);
            this.rack = newRack;
        }
        if (newLevel != null) {
            validateHierarchyField("level", newLevel);
            this.level = newLevel;
        }
        if (newBin != null) {
            validateHierarchyField("bin", newBin);
            this.bin = newBin;
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * Reject client attempts to change immutable fields. Called before
     * {@link #applyUpdate} by the application layer when a raw PATCH body carries
     * any of the immutable field names. Location immutables: {@code locationCode},
     * {@code warehouseId}, {@code zoneId}.
     */
    public void rejectImmutableChange(
            String locationCodeAttempt,
            UUID warehouseIdAttempt,
            UUID zoneIdAttempt) {
        if (locationCodeAttempt != null && !locationCodeAttempt.equals(this.locationCode)) {
            throw new ImmutableFieldException("locationCode");
        }
        if (warehouseIdAttempt != null && !warehouseIdAttempt.equals(this.warehouseId)) {
            throw new ImmutableFieldException("warehouseId");
        }
        if (zoneIdAttempt != null && !zoneIdAttempt.equals(this.zoneId)) {
            throw new ImmutableFieldException("zoneId");
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

    private static void validateWarehouseCode(String warehouseCode) {
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new ValidationException("warehouseCode is required");
        }
    }

    private static void validateWarehouseId(UUID warehouseId) {
        if (warehouseId == null) {
            throw new ValidationException("warehouseId is required");
        }
    }

    private static void validateZoneId(UUID zoneId) {
        if (zoneId == null) {
            throw new ValidationException("zoneId is required");
        }
    }

    private static void validateLocationCode(String code, String warehouseCode) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("locationCode is required");
        }
        if (code.length() > LOCATION_CODE_MAX_LENGTH) {
            throw new ValidationException(
                    "locationCode must be at most " + LOCATION_CODE_MAX_LENGTH + " characters");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ValidationException(
                    "locationCode must match pattern ^WH\\d{2,3}-[A-Z0-9]+(-[A-Z0-9]+){1,5}$");
        }
        String requiredPrefix = warehouseCode + "-";
        if (!code.startsWith(requiredPrefix)) {
            throw new ValidationException(
                    "locationCode must begin with parent warehouseCode prefix '" + requiredPrefix + "'");
        }
    }

    private static void validateHierarchyField(String fieldName, String value) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            throw new ValidationException(fieldName + " must be non-blank when provided");
        }
        if (value.length() > HIERARCHY_FIELD_MAX_LENGTH) {
            throw new ValidationException(
                    fieldName + " must be at most " + HIERARCHY_FIELD_MAX_LENGTH + " characters");
        }
        if (!HIERARCHY_COMPONENT_PATTERN.matcher(value).matches()) {
            throw new ValidationException(fieldName + " must match pattern ^[A-Z0-9]+$");
        }
    }

    private static void validateLocationType(LocationType locationType) {
        if (locationType == null) {
            throw new ValidationException("locationType is required");
        }
    }

    private static void validateCapacityUnits(Integer capacityUnits) {
        if (capacityUnits == null) {
            return;
        }
        if (capacityUnits < 1) {
            throw new ValidationException("capacityUnits must be >= 1");
        }
    }

    private static void validateActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ValidationException("actorId is required");
        }
    }

    public UUID getId() { return id; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getZoneId() { return zoneId; }
    public String getLocationCode() { return locationCode; }
    public String getAisle() { return aisle; }
    public String getRack() { return rack; }
    public String getLevel() { return level; }
    public String getBin() { return bin; }
    public LocationType getLocationType() { return locationType; }
    public Integer getCapacityUnits() { return capacityUnits; }
    public WarehouseStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
