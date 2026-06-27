package com.example.account.infrastructure.outbox;

import com.example.account.application.event.AccountEventFactory;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.util.DigestUtils;
import com.example.account.domain.account.Account;
import com.example.account.domain.event.AccountDomainEvent;
import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * {@link AccountEventPublisher} implementation (TASK-BE-451 — outbox v1 → v2).
 *
 * <p>Builds the v1 flat payload (via {@link AccountEventFactory}) and persists an
 * {@code account_outbox} row in the caller's transaction (the
 * {@code AbstractOutboxPublisher} / {@code OutboxRow} path — ADR-MONO-004 § 5).
 * The {@code AccountOutboxPublisher} relay forwards the row to Kafka asynchronously.
 *
 * <p><b>Wire-shape preserved (FLAT — no double-wrap).</b> account-service's v1
 * publisher used {@code BaseEventPublisher.saveEvent} which serialised the payload
 * map AS-IS (no canonical envelope). TASK-BE-422/423 contractually locked this flat
 * top-level shape (ecommerce account.* consumers parse root-level fields). This
 * adapter reproduces the EXACT v1 bytes: it serialises the same
 * {@link AccountDomainEvent#payload()} map directly into the row {@code payload}
 * column — it does NOT add a 7-field envelope.
 *
 * <p><b>Row PK / eventId.</b> The relay adds an additive {@code eventId} Kafka header
 * (= row PK). Where the flat payload already carries its own {@code eventId}
 * (e.g. {@code account.locked}, TASK-BE-041b, minted UUIDv7 by the factory), that
 * value is reused as the row PK so the header matches the payload. Where it does not
 * (e.g. {@code account.created} / {@code .status.changed} / {@code .deleted}), a fresh
 * UUIDv7 is minted purely for the header/PK (the flat payload is unchanged — still
 * carries NO {@code eventId}, preserving TASK-BE-422's accountId+phase dedupe).
 */
@Component
public class OutboxAccountEventPublisher implements AccountEventPublisher {

    private static final String AGGREGATE_TYPE = "Account";

    private final AccountOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final AccountEventFactory factory;
    private final Clock clock;

    public OutboxAccountEventPublisher(AccountOutboxJpaRepository outboxRepository,
                                       ObjectMapper objectMapper,
                                       AccountEventFactory factory,
                                       Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.factory = factory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publishAccountCreated(Account account, String tenantId, String locale) {
        requireTenantId(tenantId);
        String emailHash = DigestUtils.sha256Short(account.getEmail(), 10);
        save(account.getId(), factory.createdEvent(account, emailHash, locale));
    }

    @Override
    @Transactional
    public void publishStatusChanged(Account account, String tenantId, String previousStatus,
                                     String reasonCode, String actorType, String actorId,
                                     Instant occurredAt) {
        requireTenantId(tenantId);
        save(account.getId(), factory.statusChangedEvent(account, previousStatus, reasonCode,
                actorType, actorId, occurredAt));
    }

    @Override
    @Transactional
    public void publishAccountLocked(Account account, String tenantId, String reasonCode,
                                     String actorType, String actorId, Instant lockedAt) {
        requireTenantId(tenantId);
        save(account.getId(), factory.lockedEvent(account, reasonCode, actorType, actorId, lockedAt));
    }

    @Override
    @Transactional
    public void publishAccountUnlocked(Account account, String tenantId, String reasonCode,
                                       String actorType, String actorId, Instant unlockedAt) {
        requireTenantId(tenantId);
        save(account.getId(), factory.unlockedEvent(account, reasonCode, actorType, actorId, unlockedAt));
    }

    @Override
    @Transactional
    public void publishAccountDeleted(Account account, String tenantId, String reasonCode,
                                      String actorType, String actorId,
                                      Instant deletedAt, Instant gracePeriodEndsAt) {
        requireTenantId(tenantId);
        save(account.getId(), factory.deletedEvent(account, reasonCode, actorType, actorId,
                deletedAt, gracePeriodEndsAt, false));
    }

    @Override
    @Transactional
    public void publishRolesChanged(Account account, String tenantId,
                                    java.util.List<String> beforeRoles,
                                    java.util.List<String> afterRoles,
                                    String changedBy,
                                    String actorType, String actorId,
                                    Instant occurredAt) {
        requireTenantId(tenantId);
        save(account.getId(), factory.rolesChangedEvent(account,
                beforeRoles, afterRoles, changedBy, actorType, actorId, occurredAt));
    }

    @Override
    @Transactional
    public void publishAccountDeletedAnonymized(Account account, String tenantId, String reasonCode,
                                                String actorType, String actorId,
                                                Instant deletedAt, Instant gracePeriodEndsAt) {
        requireTenantId(tenantId);
        save(account.getId(), factory.deletedEvent(account, reasonCode, actorType, actorId,
                deletedAt, gracePeriodEndsAt, true));
    }

    private void save(String accountId, AccountDomainEvent event) {
        writeFlat(AGGREGATE_TYPE, accountId, event.eventType(), event.payload());
    }

    /**
     * Guard: tenantId must be non-null and non-blank (TASK-BE-248). Verbatim from
     * the v1 publisher.
     */
    private static void requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId required");
        }
    }

    /**
     * Serialise the FLAT payload map AS-IS (byte-identical to the v1
     * {@code BaseEventPublisher.saveEvent} wire — no envelope wrapper) and persist a
     * pending {@code account_outbox} row. The row PK = the payload's embedded
     * {@code eventId} when present (account.locked), else a fresh UUIDv7;
     * {@code partition_key = aggregateId} (the v1 Kafka key).
     */
    private void writeFlat(String aggregateType, String aggregateId,
                           String eventType, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox payload", e);
        }

        UUID rowId = embeddedEventIdOrFresh(payload);
        outboxRepository.save(AccountOutboxJpaEntity.create(
                rowId, aggregateType, aggregateId, eventType, json, aggregateId,
                Instant.now(clock)));
    }

    /** Reuse the flat payload's own {@code eventId} as the row PK if it is a valid UUID. */
    private static UUID embeddedEventIdOrFresh(Map<String, Object> payload) {
        Object embedded = payload.get("eventId");
        if (embedded instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                // fall through to a fresh id
            }
        }
        return UuidV7.randomUuid();
    }
}
