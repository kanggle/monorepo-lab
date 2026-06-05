package com.example.account.domain.history;

import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistoryEntry {

    private Long id;
    /**
     * TASK-BE-231: tenantId carried alongside audit entry so that
     * {@code account_status_history.tenant_id} can be populated correctly for
     * non-fan-platform provisioning calls. {@code null} for legacy fan-platform records
     * (infrastructure adapter falls back to {@code "fan-platform"} when null).
     */
    private String tenantId;
    private String accountId;
    private AccountStatus fromStatus;
    private AccountStatus toStatus;
    private StatusChangeReason reasonCode;
    private String actorType;
    private String actorId;
    private String details;
    private Instant occurredAt;

    public static AccountStatusHistoryEntry create(String accountId,
                                                    AccountStatus fromStatus,
                                                    AccountStatus toStatus,
                                                    StatusChangeReason reasonCode,
                                                    String actorType,
                                                    String actorId,
                                                    String details) {
        AccountStatusHistoryEntry entry = new AccountStatusHistoryEntry();
        entry.accountId = accountId;
        entry.fromStatus = fromStatus;
        entry.toStatus = toStatus;
        entry.reasonCode = reasonCode;
        entry.actorType = actorType;
        entry.actorId = actorId;
        entry.details = details;
        entry.occurredAt = Instant.now();
        return entry;
    }

    /**
     * TASK-BE-231: Creates an audit entry with an explicit tenantId for provisioning flows.
     */
    public static AccountStatusHistoryEntry create(String tenantId,
                                                    String accountId,
                                                    AccountStatus fromStatus,
                                                    AccountStatus toStatus,
                                                    StatusChangeReason reasonCode,
                                                    String actorType,
                                                    String actorId,
                                                    String details) {
        AccountStatusHistoryEntry entry = create(accountId, fromStatus, toStatus, reasonCode,
                actorType, actorId, details);
        entry.tenantId = tenantId;
        return entry;
    }

    /**
     * Reconstitute from persisted state. Used by infrastructure mappers.
     */
    public static AccountStatusHistoryEntry reconstitute(Long id, String accountId,
                                                          AccountStatus fromStatus,
                                                          AccountStatus toStatus,
                                                          StatusChangeReason reasonCode,
                                                          String actorType,
                                                          String actorId,
                                                          String details,
                                                          Instant occurredAt) {
        AccountStatusHistoryEntry entry = new AccountStatusHistoryEntry();
        entry.id = id;
        entry.accountId = accountId;
        entry.fromStatus = fromStatus;
        entry.toStatus = toStatus;
        entry.reasonCode = reasonCode;
        entry.actorType = actorType;
        entry.actorId = actorId;
        entry.details = details;
        entry.occurredAt = occurredAt;
        return entry;
    }
}
