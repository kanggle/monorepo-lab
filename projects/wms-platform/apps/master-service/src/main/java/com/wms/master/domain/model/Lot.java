package com.wms.master.domain.model;

import com.example.common.id.UuidV7;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lot aggregate root — a specific manufactured batch of a SKU. See
 * {@code specs/services/master-service/domain-model.md} §6 for the
 * authoritative invariants and
 * {@code specs/contracts/http/master-service-api.md} §6 for the HTTP surface.
 *
 * <p>Framework-free POJO. JPA/Spring annotations live on the adapter-side
 * {@code LotJpaEntity}.
 *
 * <p>Parent-aggregate coupling rules (parent SKU must be {@code ACTIVE} and
 * {@code trackingType = LOT} at creation time) live in
 * {@code LotService.create} — the domain factory here enforces intra-aggregate
 * invariants only (lot_no format, date pair).
 *
 * <p>Immutable post-creation: {@code skuId}, {@code lotNo}, {@code manufacturedDate}.
 * Mutations are rejected via {@link ImmutableFieldException} (422).
 *
 * <p>State machine:
 * <pre>
 *        [create]
 *           |
 *           v
 *        ACTIVE ----[deactivate]----> INACTIVE
 *           |                              ^
 *       [expire]                     [reactivate]
 *           |                              |
 *           v                              |
 *       EXPIRED (terminal) <--[deactivate from EXPIRED]--
 * </pre>
 */
public final class Lot {

    private static final int LOT_NO_MAX_LENGTH = 40;

    private UUID id;
    private UUID skuId;
    private String lotNo;
    private LocalDate manufacturedDate;
    private LocalDate expiryDate;
    private UUID supplierPartnerId;
    private LotStatus status;
    private long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Lot() {}

    /**
     * Factory for brand-new Lots. Cross-aggregate invariants (parent SKU must
     * be ACTIVE and LOT-tracked) are enforced by the application service —
     * this factory only validates intra-aggregate invariants.
     */
    public static Lot create(
            UUID skuId,
            String lotNo,
            LocalDate manufacturedDate,
            LocalDate expiryDate,
            UUID supplierPartnerId,
            String actorId) {
        validateSkuId(skuId);
        String normalizedLotNo = normalizeLotNo(lotNo);
        validateDatePair(manufacturedDate, expiryDate);
        validateActor(actorId);

        Instant now = Instant.now();
        Lot lot = new Lot();
        lot.id = UuidV7.randomUuid();
        lot.skuId = skuId;
        lot.lotNo = normalizedLotNo;
        lot.manufacturedDate = manufacturedDate;
        lot.expiryDate = expiryDate;
        lot.supplierPartnerId = supplierPartnerId;
        lot.status = LotStatus.ACTIVE;
        lot.version = 0L;
        lot.createdAt = now;
        lot.createdBy = actorId;
        lot.updatedAt = now;
        lot.updatedBy = actorId;
        return lot;
    }

    public static Lot reconstitute(
            UUID id,
            UUID skuId,
            String lotNo,
            LocalDate manufacturedDate,
            LocalDate expiryDate,
            UUID supplierPartnerId,
            LotStatus status,
            long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        Lot lot = new Lot();
        lot.id = id;
        lot.skuId = skuId;
        lot.lotNo = lotNo;
        lot.manufacturedDate = manufacturedDate;
        lot.expiryDate = expiryDate;
        lot.supplierPartnerId = supplierPartnerId;
        lot.status = status;
        lot.version = version;
        lot.createdAt = createdAt;
        lot.createdBy = createdBy;
        lot.updatedAt = updatedAt;
        lot.updatedBy = updatedBy;
        return lot;
    }

    /**
     * Apply a partial mutation. Null arguments mean "no change". Only the
     * fields listed below are mutable; {@code skuId}, {@code lotNo}, and
     * {@code manufacturedDate} are rejected via
     * {@link #rejectImmutableChange(UUID, String, LocalDate)}.
     *
     * <p>When updating {@code expiryDate}, the date-pair invariant is checked
     * against the current {@code manufacturedDate}.
     */
    public void applyUpdate(
            LocalDate newExpiryDate,
            UUID newSupplierPartnerId,
            boolean clearSupplierPartnerId,
            String actorId) {
        validateActor(actorId);
        if (newExpiryDate != null) {
            validateDatePair(this.manufacturedDate, newExpiryDate);
            this.expiryDate = newExpiryDate;
        }
        if (clearSupplierPartnerId) {
            this.supplierPartnerId = null;
        } else if (newSupplierPartnerId != null) {
            this.supplierPartnerId = newSupplierPartnerId;
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * Reject client attempts to change immutable fields. {@code lotNo} is
     * matched case-sensitively (unlike {@code skuCode}, {@code lotNo} is
     * vendor-assigned and case-significant).
     */
    public void rejectImmutableChange(UUID skuIdAttempt, String lotNoAttempt, LocalDate manufacturedDateAttempt) {
        if (skuIdAttempt != null && !skuIdAttempt.equals(this.skuId)) {
            throw new ImmutableFieldException("skuId");
        }
        if (lotNoAttempt != null && !lotNoAttempt.equals(this.lotNo)) {
            throw new ImmutableFieldException("lotNo");
        }
        if (manufacturedDateAttempt != null && !manufacturedDateAttempt.equals(this.manufacturedDate)) {
            throw new ImmutableFieldException("manufacturedDate");
        }
    }

    /**
     * ACTIVE → INACTIVE, or EXPIRED → INACTIVE. INACTIVE → INACTIVE raises
     * {@link InvalidStateTransitionException}.
     */
    public void deactivate(String actorId) {
        validateActor(actorId);
        if (this.status == LotStatus.INACTIVE) {
            throw new InvalidStateTransitionException(this.status.name(), "deactivate");
        }
        this.status = LotStatus.INACTIVE;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * INACTIVE → ACTIVE. ACTIVE → reactivate and EXPIRED → reactivate both
     * raise {@link InvalidStateTransitionException} (EXPIRED is terminal for
     * reactivation per {@code domain-model.md} §6).
     */
    public void reactivate(String actorId) {
        validateActor(actorId);
        if (this.status != LotStatus.INACTIVE) {
            throw new InvalidStateTransitionException(this.status.name(), "reactivate");
        }
        this.status = LotStatus.ACTIVE;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * ACTIVE → EXPIRED. Called only from the scheduled expiration batch (no
     * HTTP endpoint). The caller is the domain's own batch; {@code actorId}
     * identifies the system actor (typically {@code "system"} when the outbox
     * envelope's {@code actorId} will be {@code null} — see
     * {@code LotExpiredEvent}).
     */
    public void expire(String actorId) {
        validateActor(actorId);
        if (this.status != LotStatus.ACTIVE) {
            throw new InvalidStateTransitionException(this.status.name(), "expire");
        }
        this.status = LotStatus.EXPIRED;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public boolean isActive() {
        return this.status == LotStatus.ACTIVE;
    }

    public boolean isExpired() {
        return this.status == LotStatus.EXPIRED;
    }

    private static void validateSkuId(UUID skuId) {
        if (skuId == null) {
            throw new ValidationException("skuId is required");
        }
    }

    private static String normalizeLotNo(String lotNo) {
        if (lotNo == null) {
            throw new ValidationException("lotNo is required");
        }
        String trimmed = lotNo.strip();
        if (trimmed.isEmpty()) {
            throw new ValidationException("lotNo must not be blank");
        }
        if (trimmed.length() > LOT_NO_MAX_LENGTH) {
            throw new ValidationException(
                    "lotNo must be at most " + LOT_NO_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static void validateDatePair(LocalDate manufacturedDate, LocalDate expiryDate) {
        if (manufacturedDate == null || expiryDate == null) {
            return;
        }
        if (expiryDate.isBefore(manufacturedDate)) {
            throw new ValidationException(
                    "expiryDate must be on or after manufacturedDate");
        }
    }

    private static void validateActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ValidationException("actorId is required");
        }
    }

    public UUID getId() { return id; }
    public UUID getSkuId() { return skuId; }
    public String getLotNo() { return lotNo; }
    public LocalDate getManufacturedDate() { return manufacturedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public UUID getSupplierPartnerId() { return supplierPartnerId; }
    public LotStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lot other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
