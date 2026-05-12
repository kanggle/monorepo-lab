package com.wms.master.domain.model;

import com.example.common.id.UuidV7;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Partner aggregate root — an external counterparty (supplier / customer /
 * both). See {@code specs/services/master-service/domain-model.md} §5 for the
 * authoritative invariants.
 *
 * <p>Framework-free POJO. JPA / Spring annotations live on the adapter-side
 * {@code PartnerJpaEntity}.
 *
 * <p>{@code partnerCode} is immutable after creation — attempts to change it
 * are rejected with {@link ImmutableFieldException}. {@code partnerType} is
 * mutable (a relationship may evolve from supplier-only to also-customer).
 *
 * <p>Contact fields (name / email / phone) are <strong>operational B2B
 * contact data, not consumer PII</strong> per {@code PROJECT.md} `data_sensitivity:
 * internal` and {@code architecture.md} § Security.
 */
public final class Partner {

    private static final int PARTNER_CODE_MAX_LENGTH = 20;
    private static final int NAME_MAX_LENGTH = 200;
    private static final int BUSINESS_NUMBER_MAX_LENGTH = 20;
    private static final int CONTACT_NAME_MAX_LENGTH = 100;
    private static final int CONTACT_EMAIL_MAX_LENGTH = 200;
    private static final int CONTACT_PHONE_MAX_LENGTH = 30;
    private static final int ADDRESS_MAX_LENGTH = 300;

    private UUID id;
    private String partnerCode;
    private String name;
    private PartnerType partnerType;
    private String businessNumber;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private WarehouseStatus status;
    private long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Partner() {}

    public static Partner create(
            String partnerCode,
            String name,
            PartnerType partnerType,
            String businessNumber,
            String contactName,
            String contactEmail,
            String contactPhone,
            String address,
            String actorId) {
        validateActor(actorId);
        String trimmedCode = normalizePartnerCode(partnerCode);
        validateName(name);
        validatePartnerType(partnerType);
        validateBusinessNumber(businessNumber);
        validateContactName(contactName);
        validateContactEmail(contactEmail);
        validateContactPhone(contactPhone);
        validateAddress(address);

        Instant now = Instant.now();
        Partner partner = new Partner();
        partner.id = UuidV7.randomUuid();
        partner.partnerCode = trimmedCode;
        partner.name = name;
        partner.partnerType = partnerType;
        partner.businessNumber = businessNumber;
        partner.contactName = contactName;
        partner.contactEmail = contactEmail;
        partner.contactPhone = contactPhone;
        partner.address = address;
        partner.status = WarehouseStatus.ACTIVE;
        partner.version = 0L;
        partner.createdAt = now;
        partner.createdBy = actorId;
        partner.updatedAt = now;
        partner.updatedBy = actorId;
        return partner;
    }

    public static Partner reconstitute(
            UUID id,
            String partnerCode,
            String name,
            PartnerType partnerType,
            String businessNumber,
            String contactName,
            String contactEmail,
            String contactPhone,
            String address,
            WarehouseStatus status,
            long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        Partner partner = new Partner();
        partner.id = id;
        partner.partnerCode = partnerCode;
        partner.name = name;
        partner.partnerType = partnerType;
        partner.businessNumber = businessNumber;
        partner.contactName = contactName;
        partner.contactEmail = contactEmail;
        partner.contactPhone = contactPhone;
        partner.address = address;
        partner.status = status;
        partner.version = version;
        partner.createdAt = createdAt;
        partner.createdBy = createdBy;
        partner.updatedAt = updatedAt;
        partner.updatedBy = updatedBy;
        return partner;
    }

    /**
     * Apply a partial mutation. Null arguments mean "no change". Only the
     * fields listed below are mutable; {@code partnerCode} is rejected via
     * {@link #rejectImmutableChange(String)}.
     */
    public void applyUpdate(
            String newName,
            PartnerType newPartnerType,
            String newBusinessNumber,
            String newContactName,
            String newContactEmail,
            String newContactPhone,
            String newAddress,
            String actorId) {
        validateActor(actorId);
        if (newName != null) {
            validateName(newName);
            this.name = newName;
        }
        if (newPartnerType != null) {
            this.partnerType = newPartnerType;
        }
        if (newBusinessNumber != null) {
            validateBusinessNumber(newBusinessNumber);
            this.businessNumber = newBusinessNumber;
        }
        if (newContactName != null) {
            validateContactName(newContactName);
            this.contactName = newContactName;
        }
        if (newContactEmail != null) {
            validateContactEmail(newContactEmail);
            this.contactEmail = newContactEmail;
        }
        if (newContactPhone != null) {
            validateContactPhone(newContactPhone);
            this.contactPhone = newContactPhone;
        }
        if (newAddress != null) {
            validateAddress(newAddress);
            this.address = newAddress;
        }
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * Reject client attempts to change immutable fields. Called before
     * {@link #applyUpdate} by the application layer when a raw PATCH body
     * carries the immutable field {@code partnerCode}. Matching values
     * (caller sends the stored code unchanged) are tolerated as no-op.
     */
    public void rejectImmutableChange(String partnerCodeAttempt) {
        if (partnerCodeAttempt != null && !partnerCodeAttempt.equals(this.partnerCode)) {
            throw new ImmutableFieldException("partnerCode");
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

    private static String normalizePartnerCode(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            throw new ValidationException("partnerCode is required");
        }
        String trimmed = partnerCode.strip();
        if (trimmed.length() > PARTNER_CODE_MAX_LENGTH) {
            throw new ValidationException(
                    "partnerCode must be at most " + PARTNER_CODE_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new ValidationException(
                    "name must be at most " + NAME_MAX_LENGTH + " characters");
        }
    }

    private static void validatePartnerType(PartnerType partnerType) {
        if (partnerType == null) {
            throw new ValidationException("partnerType is required");
        }
    }

    private static void validateBusinessNumber(String businessNumber) {
        if (businessNumber == null) {
            return;
        }
        if (businessNumber.length() > BUSINESS_NUMBER_MAX_LENGTH) {
            throw new ValidationException(
                    "businessNumber must be at most " + BUSINESS_NUMBER_MAX_LENGTH + " characters");
        }
    }

    private static void validateContactName(String contactName) {
        if (contactName == null) {
            return;
        }
        if (contactName.length() > CONTACT_NAME_MAX_LENGTH) {
            throw new ValidationException(
                    "contactName must be at most " + CONTACT_NAME_MAX_LENGTH + " characters");
        }
    }

    private static void validateContactEmail(String contactEmail) {
        if (contactEmail == null) {
            return;
        }
        if (contactEmail.length() > CONTACT_EMAIL_MAX_LENGTH) {
            throw new ValidationException(
                    "contactEmail must be at most " + CONTACT_EMAIL_MAX_LENGTH + " characters");
        }
        // Minimal email shape check; full RFC5321 left to bean-validation at
        // the DTO layer. Domain rejects only "obviously not an email".
        if (!contactEmail.isBlank() && !contactEmail.contains("@")) {
            throw new ValidationException("contactEmail must contain '@'");
        }
    }

    private static void validateContactPhone(String contactPhone) {
        if (contactPhone == null) {
            return;
        }
        if (contactPhone.length() > CONTACT_PHONE_MAX_LENGTH) {
            throw new ValidationException(
                    "contactPhone must be at most " + CONTACT_PHONE_MAX_LENGTH + " characters");
        }
    }

    private static void validateAddress(String address) {
        if (address == null) {
            return;
        }
        if (address.length() > ADDRESS_MAX_LENGTH) {
            throw new ValidationException(
                    "address must be at most " + ADDRESS_MAX_LENGTH + " characters");
        }
    }

    private static void validateActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ValidationException("actorId is required");
        }
    }

    public UUID getId() { return id; }
    public String getPartnerCode() { return partnerCode; }
    public String getName() { return name; }
    public PartnerType getPartnerType() { return partnerType; }
    public String getBusinessNumber() { return businessNumber; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getAddress() { return address; }
    public WarehouseStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Partner other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
