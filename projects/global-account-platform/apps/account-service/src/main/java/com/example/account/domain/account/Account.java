package com.example.account.domain.account;

import com.example.account.domain.event.AccountDomainEvent;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.status.StatusTransition;
import com.example.account.domain.tenant.TenantId;
import com.example.common.id.UuidV7;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregate root for account domain.
 *
 * <p>Every account belongs to exactly one tenant ({@link TenantId}). The tenant
 * determines the data isolation boundary — all repository queries must pass the
 * tenantId to prevent cross-tenant leaks.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    private String id;
    private TenantId tenantId;
    private String email;
    private String emailHash;
    private AccountStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private Instant lastLoginSucceededAt;
    /**
     * TASK-BE-114: email verification timestamp.
     *
     * <p>{@code null} means the email has not been verified yet. The account
     * stays ACTIVE either way — verification is non-blocking. Set exactly once
     * by {@link #verifyEmail(Instant)}; subsequent attempts throw
     * {@link IllegalStateException}.</p>
     */
    private Instant emailVerifiedAt;
    private int version;

    /**
     * Create a new account under the given tenant.
     *
     * @param tenantId the owning tenant (must not be null)
     * @param email    the account's email address (validated by {@link Email})
     */
    public static Account create(TenantId tenantId, String email) {
        Email validatedEmail = new Email(email);

        Account account = new Account();
        account.id = AccountId.generate().value();
        account.tenantId = tenantId;
        account.email = validatedEmail.value();
        account.status = AccountStatus.ACTIVE;
        account.createdAt = Instant.now();
        account.updatedAt = Instant.now();
        account.version = 0;
        return account;
    }

    /**
     * Reconstitute an Account from persisted state. Used by infrastructure mappers.
     */
    public static Account reconstitute(String id, TenantId tenantId, String email, String emailHash,
                                        AccountStatus status,
                                        Instant createdAt, Instant updatedAt,
                                        Instant deletedAt,
                                        Instant lastLoginSucceededAt,
                                        Instant emailVerifiedAt,
                                        int version) {
        Account account = new Account();
        account.id = id;
        account.tenantId = tenantId;
        account.email = email;
        account.emailHash = emailHash;
        account.status = status;
        account.createdAt = createdAt;
        account.updatedAt = updatedAt;
        account.deletedAt = deletedAt;
        account.lastLoginSucceededAt = lastLoginSucceededAt;
        account.emailVerifiedAt = emailVerifiedAt;
        account.version = version;
        return account;
    }

    /**
     * Apply GDPR PII masking to the email field.
     * Replaces email with a non-reversible masked value and stores the SHA-256 hash.
     */
    public void maskEmail(String hashedEmail, String maskedEmail) {
        this.emailHash = hashedEmail;
        this.email = maskedEmail;
        this.updatedAt = Instant.now();
    }

    /**
     * Record a successful login at the given instant.
     *
     * <p>Uses max semantics — the field is only advanced if the new instant is
     * strictly more recent than the value already stored. This guards against
     * out-of-order delivery of {@code auth.login.succeeded} Kafka events
     * (replay, partition rebalance, redelivery) where an older event might
     * arrive after a newer one. See TASK-BE-103 / specs/contracts/events/auth-events.md.
     *
     * @param occurredAt the timestamp of the successful login (UTC); never null
     */
    public void recordLoginSuccess(Instant occurredAt) {
        if (this.lastLoginSucceededAt == null || occurredAt.isAfter(this.lastLoginSucceededAt)) {
            this.lastLoginSucceededAt = occurredAt;
        }
    }

    /**
     * Mark this account's email as verified at the given instant.
     *
     * <p>TASK-BE-114: idempotent first-write only. The field is set exactly
     * once — re-verification is rejected with {@link IllegalStateException}
     * so the application layer can surface a 409 {@code EMAIL_ALREADY_VERIFIED}
     * to the caller.</p>
     *
     * <p>Verification does <strong>not</strong> change account status — the
     * platform's signup flow is non-blocking and accounts stay ACTIVE either
     * way. Only {@code emailVerifiedAt} and {@code updatedAt} change.</p>
     *
     * @param now timestamp at which verification occurred (UTC); never null
     * @throws IllegalStateException if {@code emailVerifiedAt} is already set
     */
    public void verifyEmail(Instant now) {
        if (this.emailVerifiedAt != null) {
            throw new IllegalStateException("Email is already verified");
        }
        this.emailVerifiedAt = now;
        this.updatedAt = now;
    }

    /**
     * Transition account status via the state machine.
     * Returns the validated transition for recording in history.
     */
    public StatusTransition changeStatus(AccountStatusMachine machine,
                                         AccountStatus targetStatus,
                                         StatusChangeReason reason) {
        StatusTransition transition = machine.transition(this.status, targetStatus, reason);

        if (this.status != targetStatus) {
            this.status = targetStatus;
            this.updatedAt = Instant.now();

            if (targetStatus == AccountStatus.DELETED) {
                this.deletedAt = Instant.now();
            } else if (this.deletedAt != null) {
                // Recovering from DELETED state
                this.deletedAt = null;
            }
        }

        return transition;
    }

    public AccountDomainEvent buildCreatedEvent(String emailHash, String locale) {
        return new AccountDomainEvent("account.created", Map.of(
                "accountId", id,
                "tenantId", tenantId.value(),
                "emailHash", emailHash,
                "status", status.name(),
                "locale", locale,
                "createdAt", createdAt.toString()
        ));
    }

    public AccountDomainEvent buildStatusChangedEvent(String previousStatus, String reasonCode,
                                                       String actorType, String actorId,
                                                       Instant occurredAt) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "accountId", id,
                "tenantId", tenantId.value(),
                "previousStatus", previousStatus,
                "currentStatus", status.name(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "occurredAt", occurredAt.toString()
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.status.changed", payload);
    }

    public AccountDomainEvent buildLockedEvent(String reasonCode, String actorType,
                                                String actorId, Instant lockedAt) {
        // TASK-BE-118: account.locked.eventId must be UUID v7 per
        // specs/contracts/events/account-events.md so security-service can use it
        // as a time-ordered idempotency key (account_lock_history.event_id).
        Map<String, Object> payload = new HashMap<>(Map.of(
                "eventId", UuidV7.randomString(),
                "accountId", id,
                "tenantId", tenantId.value(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "lockedAt", lockedAt.toString()
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.locked", payload);
    }

    public AccountDomainEvent buildUnlockedEvent(String reasonCode, String actorType,
                                                  String actorId, Instant unlockedAt) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "accountId", id,
                "tenantId", tenantId.value(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "unlockedAt", unlockedAt.toString()
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.unlocked", payload);
    }

    /**
     * TASK-BE-231: Event emitted when the provisioning API mutates an account's role set.
     *
     * <p>TASK-BE-255: Schema bumped to v3. Adds {@code beforeRoles}, {@code afterRoles}
     * and {@code changedBy} fields so downstream consumers can compute the diff
     * directly without a separate snapshot store. The legacy {@code roles} field
     * is preserved as an alias for {@code afterRoles} (forward-compat).
     */
    public AccountDomainEvent buildRolesChangedEvent(java.util.List<String> beforeRoles,
                                                      java.util.List<String> afterRoles,
                                                      String changedBy,
                                                      String actorType, String actorId,
                                                      Instant occurredAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", id);
        payload.put("tenantId", tenantId.value());
        payload.put("roles", afterRoles);            // legacy alias (v2 consumers)
        payload.put("beforeRoles", beforeRoles);     // TASK-BE-255 (v3)
        payload.put("afterRoles", afterRoles);       // TASK-BE-255 (v3)
        payload.put("actorType", actorType);
        payload.put("occurredAt", occurredAt.toString());
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        if (changedBy != null) {
            payload.put("changedBy", changedBy);     // TASK-BE-255 (v3)
        }
        return new AccountDomainEvent("account.roles.changed", payload);
    }

    public AccountDomainEvent buildDeletedEvent(String reasonCode, String actorType,
                                                 String actorId, Instant deletedAt,
                                                 Instant gracePeriodEndsAt, boolean anonymized) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "accountId", id,
                "tenantId", tenantId.value(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "deletedAt", deletedAt.toString(),
                "gracePeriodEndsAt", gracePeriodEndsAt.toString(),
                "anonymized", anonymized
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.deleted", payload);
    }
}
