package com.example.account.domain.account;

import com.example.account.domain.event.AccountDomainEvent;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Account 도메인 이벤트 팩토리 메서드 단위 테스트")
class AccountDomainEventTest {

    private static final String EMAIL = "user@example.com";
    private static final String EMAIL_HASH = "abc1234567";
    private static final String LOCALE = "ko-KR";
    private static final String ACTOR_TYPE = "operator";
    private static final String ACTOR_ID = "op-1";
    private static final String REASON_CODE = "ADMIN_LOCK";

    private Account newActiveAccount() {
        return Account.create(TenantId.FAN_PLATFORM, EMAIL);
    }

    @Test
    @DisplayName("buildCreatedEvent — 계약 필드(accountId, tenantId, emailHash, status, locale, createdAt)가 포함된다")
    void buildCreatedEvent_containsAllContractFields() {
        Account account = newActiveAccount();

        AccountDomainEvent event = account.buildCreatedEvent(EMAIL_HASH, LOCALE);

        assertThat(event.eventType()).isEqualTo("account.created");
        Map<String, Object> p = event.payload();
        assertThat(p.get("accountId")).isEqualTo(account.getId());
        assertThat(p.get("tenantId")).isEqualTo("fan-platform");
        assertThat(p.get("emailHash")).isEqualTo(EMAIL_HASH);
        assertThat(p.get("status")).isEqualTo("ACTIVE");
        assertThat(p.get("locale")).isEqualTo(LOCALE);
        assertThat(p.get("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("buildStatusChangedEvent — actorId null이면 payload에 포함되지 않는다")
    void buildStatusChangedEvent_nullActorId_notInPayload() {
        Account account = newActiveAccount();
        Instant now = Instant.now();

        AccountDomainEvent event = account.buildStatusChangedEvent(
                "ACTIVE", "ADMIN_LOCK", ACTOR_TYPE, null, now);

        assertThat(event.eventType()).isEqualTo("account.status.changed");
        assertThat(event.payload()).doesNotContainKey("actorId");
        assertThat(event.payload().get("previousStatus")).isEqualTo("ACTIVE");
        assertThat(event.payload().get("currentStatus")).isEqualTo("ACTIVE");
        assertThat(event.payload().get("reasonCode")).isEqualTo("ADMIN_LOCK");
        assertThat(event.payload().get("actorType")).isEqualTo(ACTOR_TYPE);
        assertThat(event.payload().get("occurredAt")).isEqualTo(now.toString());
    }

    @Test
    @DisplayName("buildStatusChangedEvent — actorId 존재 시 payload에 포함된다")
    void buildStatusChangedEvent_withActorId_inPayload() {
        Account account = newActiveAccount();

        AccountDomainEvent event = account.buildStatusChangedEvent(
                "ACTIVE", REASON_CODE, ACTOR_TYPE, ACTOR_ID, Instant.now());

        assertThat(event.payload().get("actorId")).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("buildLockedEvent — eventId(UUID v7), reasonCode, lockedAt 포함, actorId null이면 제외")
    void buildLockedEvent_containsEventIdAndExcludesNullActorId() {
        Account account = newActiveAccount();
        Instant lockedAt = Instant.now();

        AccountDomainEvent event = account.buildLockedEvent(REASON_CODE, ACTOR_TYPE, null, lockedAt);

        assertThat(event.eventType()).isEqualTo("account.locked");
        Map<String, Object> p = event.payload();
        assertThat(p.get("eventId")).isNotNull().asString().isNotBlank();
        // TASK-BE-118: eventId must be UUID v7 per
        // specs/contracts/events/account-events.md (account.locked).
        UUID parsedEventId = UUID.fromString((String) p.get("eventId"));
        assertThat(parsedEventId.version())
                .as("account.locked.eventId must be UUID v7 (RFC 9562)")
                .isEqualTo(7);
        assertThat(p.get("accountId")).isEqualTo(account.getId());
        assertThat(p.get("reasonCode")).isEqualTo(REASON_CODE);
        assertThat(p.get("actorType")).isEqualTo(ACTOR_TYPE);
        assertThat(p.get("lockedAt")).isEqualTo(lockedAt.toString());
        assertThat(p).doesNotContainKey("actorId");
    }

    @Test
    @DisplayName("buildLockedEvent — 호출마다 다른 UUID v7 eventId가 생성된다 (유니크성)")
    void buildLockedEvent_eachCallProducesUniqueEventId() {
        Account account = newActiveAccount();

        String id1 = (String) account.buildLockedEvent(REASON_CODE, ACTOR_TYPE, null, Instant.now()).payload().get("eventId");
        String id2 = (String) account.buildLockedEvent(REASON_CODE, ACTOR_TYPE, null, Instant.now()).payload().get("eventId");

        assertThat(id1).isNotEqualTo(id2);
        // TASK-BE-118: both eventIds must be UUID v7.
        assertThat(UUID.fromString(id1).version()).isEqualTo(7);
        assertThat(UUID.fromString(id2).version()).isEqualTo(7);
    }

    @Test
    @DisplayName("buildUnlockedEvent — 계약 필드 포함, actorId 있으면 payload에 포함")
    void buildUnlockedEvent_withActorId_inPayload() {
        Account account = newActiveAccount();
        Instant unlockedAt = Instant.now();

        AccountDomainEvent event = account.buildUnlockedEvent("ADMIN_UNLOCK", ACTOR_TYPE, ACTOR_ID, unlockedAt);

        assertThat(event.eventType()).isEqualTo("account.unlocked");
        assertThat(event.payload().get("accountId")).isEqualTo(account.getId());
        assertThat(event.payload().get("reasonCode")).isEqualTo("ADMIN_UNLOCK");
        assertThat(event.payload().get("unlockedAt")).isEqualTo(unlockedAt.toString());
        assertThat(event.payload().get("actorId")).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("buildDeletedEvent(anonymized=false) — gracePeriodEndsAt, anonymized=false 포함")
    void buildDeletedEvent_notAnonymized_payloadCorrect() {
        Account account = newActiveAccount();
        Instant deletedAt = Instant.now();
        Instant gracePeriodEndsAt = deletedAt.plusSeconds(30L * 24 * 3600);

        AccountDomainEvent event = account.buildDeletedEvent(
                "USER_REQUEST", "user", ACTOR_ID, deletedAt, gracePeriodEndsAt, false);

        assertThat(event.eventType()).isEqualTo("account.deleted");
        assertThat(event.payload().get("anonymized")).isEqualTo(false);
        assertThat(event.payload().get("deletedAt")).isEqualTo(deletedAt.toString());
        assertThat(event.payload().get("gracePeriodEndsAt")).isEqualTo(gracePeriodEndsAt.toString());
        assertThat(event.payload().get("reasonCode")).isEqualTo("USER_REQUEST");
    }

    @Test
    @DisplayName("buildDeletedEvent(anonymized=true) — anonymized=true 포함")
    void buildDeletedEvent_anonymized_flagTrue() {
        Account account = newActiveAccount();
        Instant deletedAt = Instant.now();

        AccountDomainEvent event = account.buildDeletedEvent(
                "USER_REQUEST", "system", null, deletedAt, deletedAt.plusSeconds(100), true);

        assertThat(event.payload().get("anonymized")).isEqualTo(true);
        assertThat(event.payload()).doesNotContainKey("actorId");
    }
}
