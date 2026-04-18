package com.wms.master.domain.model;

import com.example.common.id.UuidV7;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Warehouse {

    private static final Pattern CODE_PATTERN = Pattern.compile("^WH\\d{2,3}$");
    private static final int NAME_MAX_LENGTH = 100;
    private static final int ADDRESS_MAX_LENGTH = 200;

    private UUID id;
    private String warehouseCode;
    private String name;
    private String address;
    private String timezone;
    private WarehouseStatus status;
    private long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Warehouse() {}

    public static Warehouse create(
            String warehouseCode,
            String name,
            String address,
            String timezone,
            String actorId) {
        validateCode(warehouseCode);
        validateName(name);
        validateAddress(address);
        validateTimezone(timezone);
        validateActor(actorId);

        Instant now = Instant.now();
        Warehouse warehouse = new Warehouse();
        warehouse.id = UuidV7.randomUuid();
        warehouse.warehouseCode = warehouseCode;
        warehouse.name = name;
        warehouse.address = address;
        warehouse.timezone = timezone;
        warehouse.status = WarehouseStatus.ACTIVE;
        warehouse.version = 0L;
        warehouse.createdAt = now;
        warehouse.createdBy = actorId;
        warehouse.updatedAt = now;
        warehouse.updatedBy = actorId;
        return warehouse;
    }

    public static Warehouse reconstitute(
            UUID id,
            String warehouseCode,
            String name,
            String address,
            String timezone,
            WarehouseStatus status,
            long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        Warehouse warehouse = new Warehouse();
        warehouse.id = id;
        warehouse.warehouseCode = warehouseCode;
        warehouse.name = name;
        warehouse.address = address;
        warehouse.timezone = timezone;
        warehouse.status = status;
        warehouse.version = version;
        warehouse.createdAt = createdAt;
        warehouse.createdBy = createdBy;
        warehouse.updatedAt = updatedAt;
        warehouse.updatedBy = updatedBy;
        return warehouse;
    }

    public void applyUpdate(String newName, String newAddress, String newTimezone, String actorId) {
        validateActor(actorId);
        if (newName != null) {
            validateName(newName);
            this.name = newName;
        }
        if (newAddress != null) {
            validateAddress(newAddress);
            this.address = newAddress;
        }
        if (newTimezone != null) {
            validateTimezone(newTimezone);
            this.timezone = newTimezone;
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public void rejectImmutableChange(String warehouseCodeAttempt) {
        if (warehouseCodeAttempt != null && !warehouseCodeAttempt.equals(this.warehouseCode)) {
            throw new ImmutableFieldException("warehouseCode");
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

    private static void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("warehouseCode is required");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ValidationException("warehouseCode must match pattern 'WH' followed by 2-3 digits (e.g., WH01)");
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

    private static void validateAddress(String address) {
        if (address == null) {
            return;
        }
        if (address.length() > ADDRESS_MAX_LENGTH) {
            throw new ValidationException("address must be at most " + ADDRESS_MAX_LENGTH + " characters");
        }
    }

    private static void validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new ValidationException("timezone is required");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ValidationException("timezone must be a valid IANA zone id, got: " + timezone);
        }
    }

    private static void validateActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ValidationException("actorId is required");
        }
    }

    public UUID getId() { return id; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getTimezone() { return timezone; }
    public WarehouseStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Warehouse other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
