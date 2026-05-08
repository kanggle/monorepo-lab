package com.wms.admin.domain;

import com.wms.admin.domain.error.StateTransitionInvalidException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Operator account profile aggregate. Layered architecture POJO — invariants
 * are enforced via {@code create} / mutation methods that return new instances
 * (immutable record-like shape).
 *
 * <p>State machine: {@code ACTIVE ↔ INACTIVE} only (T4). Any other transition
 * raises {@link StateTransitionInvalidException}.
 *
 * <p>Cascade revocation when force-deactivating a user with active assignments
 * is orchestrated at the application layer (UserService); the domain object
 * carries no reference to its assignments by design (separate aggregate).
 */
public final class User {

    private final UUID id;
    private final String userCode;
    private final String email;
    private final String name;
    private final String phone;
    private final UserStatus status;
    private final UUID defaultWarehouseId;
    private final long version;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant updatedAt;
    private final String updatedBy;

    public User(UUID id, String userCode, String email, String name, String phone,
                UserStatus status, UUID defaultWarehouseId, long version,
                Instant createdAt, String createdBy,
                Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.userCode = Objects.requireNonNull(userCode, "userCode");
        this.email = Objects.requireNonNull(email, "email");
        this.name = Objects.requireNonNull(name, "name");
        this.phone = phone;
        this.status = Objects.requireNonNull(status, "status");
        this.defaultWarehouseId = defaultWarehouseId;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = createdBy;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = updatedBy;
    }

    /**
     * Construct a fresh ACTIVE user. Caller is responsible for normalising
     * email (lowercasing) — the domain trusts the application layer to have
     * already validated + canonicalised.
     */
    public static User create(UUID id, String userCode, String email, String name,
                              String phone, UUID defaultWarehouseId,
                              Instant now, String actor) {
        return new User(id, userCode, email, name, phone,
                UserStatus.ACTIVE, defaultWarehouseId, 0L,
                now, actor, now, actor);
    }

    public User updateProfile(String newName, String newEmail, String newPhone,
                              UUID newDefaultWarehouseId, Instant now, String actor) {
        return new User(id, userCode,
                newEmail != null ? newEmail : email,
                newName != null ? newName : name,
                newPhone != null ? newPhone : phone,
                status,
                newDefaultWarehouseId,
                version,
                createdAt, createdBy, now, actor);
    }

    public User deactivate(Instant now, String actor) {
        if (status != UserStatus.ACTIVE) {
            throw new StateTransitionInvalidException(
                    "user " + id + " is " + status + ", cannot deactivate");
        }
        return new User(id, userCode, email, name, phone,
                UserStatus.INACTIVE, defaultWarehouseId, version,
                createdAt, createdBy, now, actor);
    }

    public User reactivate(Instant now, String actor) {
        if (status != UserStatus.INACTIVE) {
            throw new StateTransitionInvalidException(
                    "user " + id + " is " + status + ", cannot reactivate");
        }
        return new User(id, userCode, email, name, phone,
                UserStatus.ACTIVE, defaultWarehouseId, version,
                createdAt, createdBy, now, actor);
    }

    public UUID id() { return id; }
    public String userCode() { return userCode; }
    public String email() { return email; }
    public String name() { return name; }
    public String phone() { return phone; }
    public UserStatus status() { return status; }
    public UUID defaultWarehouseId() { return defaultWarehouseId; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
}
