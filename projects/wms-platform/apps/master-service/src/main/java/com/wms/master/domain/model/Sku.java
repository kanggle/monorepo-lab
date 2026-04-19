package com.wms.master.domain.model;

import com.example.common.id.UuidV7;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * SKU aggregate root — a single stock-keeping unit in the master catalog. See
 * {@code specs/services/master-service/domain-model.md} §4 for the
 * authoritative invariants.
 *
 * <p>Framework-free POJO. JPA/Spring annotations live on the adapter-side
 * {@code SkuJpaEntity}.
 *
 * <p>Case-insensitive {@code skuCode} is normalized to UPPERCASE by the
 * factory. The DB carries a plain unique constraint on the normalized column
 * (plus a {@code CHECK (sku_code = UPPER(sku_code))} guard) so downstream
 * consumers always see a consistent form.
 *
 * <p>{@code skuCode}, {@code baseUom}, and {@code trackingType} are immutable
 * after creation — changes are rejected with {@link ImmutableFieldException}.
 */
public final class Sku {

    private static final int SKU_CODE_MAX_LENGTH = 40;
    private static final int NAME_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 1000;
    private static final int BARCODE_MAX_LENGTH = 40;
    private static final int HAZARD_CLASS_MAX_LENGTH = 20;

    private UUID id;
    private String skuCode;
    private String name;
    private String description;
    private String barcode;
    private BaseUom baseUom;
    private TrackingType trackingType;
    private Integer weightGrams;
    private Integer volumeMl;
    private String hazardClass;
    private Integer shelfLifeDays;
    private WarehouseStatus status;
    private long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Sku() {}

    public static Sku create(
            String skuCode,
            String name,
            String description,
            String barcode,
            BaseUom baseUom,
            TrackingType trackingType,
            Integer weightGrams,
            Integer volumeMl,
            String hazardClass,
            Integer shelfLifeDays,
            String actorId) {
        validateActor(actorId);
        String normalizedCode = normalizeSkuCode(skuCode);
        validateName(name);
        validateDescription(description);
        validateBarcode(barcode);
        validateBaseUom(baseUom);
        validateTrackingType(trackingType);
        validateHazardClass(hazardClass);
        validateWeightGrams(weightGrams);
        validateVolumeMl(volumeMl);
        validateShelfLifeDays(shelfLifeDays);

        Instant now = Instant.now();
        Sku sku = new Sku();
        sku.id = UuidV7.randomUuid();
        sku.skuCode = normalizedCode;
        sku.name = name;
        sku.description = description;
        sku.barcode = barcode;
        sku.baseUom = baseUom;
        sku.trackingType = trackingType;
        sku.weightGrams = weightGrams;
        sku.volumeMl = volumeMl;
        sku.hazardClass = hazardClass;
        sku.shelfLifeDays = shelfLifeDays;
        sku.status = WarehouseStatus.ACTIVE;
        sku.version = 0L;
        sku.createdAt = now;
        sku.createdBy = actorId;
        sku.updatedAt = now;
        sku.updatedBy = actorId;
        return sku;
    }

    public static Sku reconstitute(
            UUID id,
            String skuCode,
            String name,
            String description,
            String barcode,
            BaseUom baseUom,
            TrackingType trackingType,
            Integer weightGrams,
            Integer volumeMl,
            String hazardClass,
            Integer shelfLifeDays,
            WarehouseStatus status,
            long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        Sku sku = new Sku();
        sku.id = id;
        sku.skuCode = skuCode;
        sku.name = name;
        sku.description = description;
        sku.barcode = barcode;
        sku.baseUom = baseUom;
        sku.trackingType = trackingType;
        sku.weightGrams = weightGrams;
        sku.volumeMl = volumeMl;
        sku.hazardClass = hazardClass;
        sku.shelfLifeDays = shelfLifeDays;
        sku.status = status;
        sku.version = version;
        sku.createdAt = createdAt;
        sku.createdBy = createdBy;
        sku.updatedAt = updatedAt;
        sku.updatedBy = updatedBy;
        return sku;
    }

    /**
     * Apply a partial mutation. Null arguments mean "no change". Only the
     * fields listed below are mutable; {@code skuCode}, {@code baseUom}, and
     * {@code trackingType} are rejected via
     * {@link #rejectImmutableChange(String, BaseUom, TrackingType)}.
     */
    public void applyUpdate(
            String newName,
            String newDescription,
            String newBarcode,
            Integer newWeightGrams,
            Integer newVolumeMl,
            String newHazardClass,
            Integer newShelfLifeDays,
            String actorId) {
        validateActor(actorId);
        if (newName != null) {
            validateName(newName);
            this.name = newName;
        }
        if (newDescription != null) {
            validateDescription(newDescription);
            this.description = newDescription;
        }
        if (newBarcode != null) {
            validateBarcode(newBarcode);
            this.barcode = newBarcode;
        }
        if (newWeightGrams != null) {
            validateWeightGrams(newWeightGrams);
            this.weightGrams = newWeightGrams;
        }
        if (newVolumeMl != null) {
            validateVolumeMl(newVolumeMl);
            this.volumeMl = newVolumeMl;
        }
        if (newHazardClass != null) {
            validateHazardClass(newHazardClass);
            this.hazardClass = newHazardClass;
        }
        if (newShelfLifeDays != null) {
            validateShelfLifeDays(newShelfLifeDays);
            this.shelfLifeDays = newShelfLifeDays;
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * Reject client attempts to change immutable fields. Called before
     * {@link #applyUpdate} by the application layer when a raw PATCH body
     * carries any of the immutable field names. {@code skuCodeAttempt} is
     * compared case-insensitively (the caller might have sent a lowercase form
     * matching the stored uppercase — still not an error, still not a change).
     */
    public void rejectImmutableChange(
            String skuCodeAttempt, BaseUom baseUomAttempt, TrackingType trackingTypeAttempt) {
        if (skuCodeAttempt != null
                && !skuCodeAttempt.toUpperCase(Locale.ROOT).equals(this.skuCode)) {
            throw new ImmutableFieldException("skuCode");
        }
        if (baseUomAttempt != null && baseUomAttempt != this.baseUom) {
            throw new ImmutableFieldException("baseUom");
        }
        if (trackingTypeAttempt != null && trackingTypeAttempt != this.trackingType) {
            throw new ImmutableFieldException("trackingType");
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

    private static String normalizeSkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new ValidationException("skuCode is required");
        }
        String trimmed = skuCode.strip();
        if (trimmed.length() > SKU_CODE_MAX_LENGTH) {
            throw new ValidationException(
                    "skuCode must be at most " + SKU_CODE_MAX_LENGTH + " characters");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("name must be at most " + NAME_MAX_LENGTH + " characters");
        }
    }

    private static void validateDescription(String description) {
        if (description == null) {
            return;
        }
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new ValidationException(
                    "description must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
    }

    private static void validateBarcode(String barcode) {
        if (barcode == null) {
            return;
        }
        if (barcode.isBlank()) {
            throw new ValidationException("barcode must not be blank when supplied");
        }
        if (barcode.length() > BARCODE_MAX_LENGTH) {
            throw new ValidationException(
                    "barcode must be at most " + BARCODE_MAX_LENGTH + " characters");
        }
    }

    private static void validateBaseUom(BaseUom baseUom) {
        if (baseUom == null) {
            throw new ValidationException("baseUom is required");
        }
    }

    private static void validateTrackingType(TrackingType trackingType) {
        if (trackingType == null) {
            throw new ValidationException("trackingType is required");
        }
    }

    private static void validateHazardClass(String hazardClass) {
        if (hazardClass == null) {
            return;
        }
        if (hazardClass.length() > HAZARD_CLASS_MAX_LENGTH) {
            throw new ValidationException(
                    "hazardClass must be at most " + HAZARD_CLASS_MAX_LENGTH + " characters");
        }
    }

    private static void validateWeightGrams(Integer weightGrams) {
        if (weightGrams == null) {
            return;
        }
        if (weightGrams < 0) {
            throw new ValidationException("weightGrams must be >= 0");
        }
    }

    private static void validateVolumeMl(Integer volumeMl) {
        if (volumeMl == null) {
            return;
        }
        if (volumeMl < 0) {
            throw new ValidationException("volumeMl must be >= 0");
        }
    }

    private static void validateShelfLifeDays(Integer shelfLifeDays) {
        if (shelfLifeDays == null) {
            return;
        }
        if (shelfLifeDays < 0) {
            throw new ValidationException("shelfLifeDays must be >= 0");
        }
    }

    private static void validateActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ValidationException("actorId is required");
        }
    }

    public UUID getId() { return id; }
    public String getSkuCode() { return skuCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBarcode() { return barcode; }
    public BaseUom getBaseUom() { return baseUom; }
    public TrackingType getTrackingType() { return trackingType; }
    public Integer getWeightGrams() { return weightGrams; }
    public Integer getVolumeMl() { return volumeMl; }
    public String getHazardClass() { return hazardClass; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public WarehouseStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sku other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
