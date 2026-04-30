package com.example.account.infrastructure.scheduler;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.account.infrastructure.persistence.AccountStatusHistoryJpaRepository;
import com.example.messaging.outbox.OutboxJpaEntity;
import com.example.messaging.outbox.OutboxJpaRepository;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AccountDormantScheduler} (TASK-BE-094, fix in TASK-BE-098).
 *
 * <p>Verifies the end-to-end batch flow against a real MySQL Testcontainer:
 * <ol>
 *   <li>365일 초과 미접속 ACTIVE 계정 → 배치 실행 → DORMANT 전환, outbox에
 *       {@code account.status.changed} 이벤트 적재</li>
 *   <li>364일 미접속 ACTIVE 계정 → 배치 실행 → 전환 없음</li>
 *   <li>365일 초과 LOCKED 계정 → 배치 실행 → 전환 없음 (금지 전이 + 쿼리에서 자연 제외)</li>
 *   <li>이미 DORMANT인 계정 → 배치 실행 → 변경 없음</li>
 * </ol>
 *
 * <p>Outbox is queried directly because the spec mandates outbox as the source of truth
 * for event publication; Kafka delivery is verified separately by the outbox relay test.
 * The Kafka template and outbox poller are mocked to avoid producer metadata lookup
 * during context startup (matches AccountSignupIntegrationTest pattern).
 *
 * <p>TASK-BE-098: extends {@link AbstractIntegrationTest} so MySQL/Kafka containers are
 * shared per-JVM (platform/testing-strategy.md "Container Lifecycle"). The internal API
 * token property is registered via a subclass {@code @DynamicPropertySource}.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("AccountDormantScheduler 통합 테스트 — 휴면 전환 배치 + outbox 발행")
class AccountDormantSchedulerIntegrationTest extends AbstractIntegrationTest {

    // TASK-BE-101: Docker availability is now enforced by DockerAvailableCondition
    // applied at the AbstractIntegrationTest level (@ExtendWith). Removing the
    // class-local @EnabledIf("isDockerAvailable") guard is mandatory because
    // resolving that condition required class init, which in turn ran
    // AbstractIntegrationTest's static block — and that crashed with
    // ExceptionInInitializerError on Docker-less developer machines, surfacing
    // as a FAILED test instead of SKIPPED. The new ExecutionCondition runs
    // before any class init, so subclasses are skipped cleanly.

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> "test-internal-token");
    }

    @Autowired
    private AccountDormantScheduler scheduler;

    @Autowired
    private AccountStatusUseCase accountStatusUseCase;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountStatusHistoryJpaRepository historyRepository;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The scheduler writes to outbox; we verify the outbox row directly. Avoid the
    // ~50s Kafka producer metadata lookup at context start by mocking both the
    // KafkaTemplate and the outbox relay (same rationale as AccountSignupIntegrationTest).
    @MockitoBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @MockitoBean
    private OutboxPollingScheduler outboxPollingScheduler;

    @Test
    @DisplayName("365일 초과 ACTIVE 계정이 DORMANT로 전환되고 account.status.changed 이벤트가 outbox에 적재된다")
    void runDormantBatch_eligibleActive_transitionsToDormantAndEnqueuesEvent() {
        String email = "dormant-eligible-" + UUID.randomUUID() + "@example.com";
        Account account = createActiveAccount(email);
        // Push last_login_succeeded_at + created_at 366 days into the past so the COALESCE
        // dormant-candidate query (retention.md §1.3/§1.4) matches.
        Instant longAgo = Instant.now().minus(366, ChronoUnit.DAYS);
        setAccountTimestamps(account.getId(), longAgo, longAgo);

        scheduler.runDormantBatch();

        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.DORMANT);

        // History append (account_status_history): exactly one row for this account.
        var history = historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(history.get(0).getToStatus()).isEqualTo(AccountStatus.DORMANT);
        assertThat(history.get(0).getReasonCode()).isEqualTo(StatusChangeReason.DORMANT_365D);
        assertThat(history.get(0).getActorType()).isEqualTo("system");
        assertThat(history.get(0).getActorId()).isNull();

        // Outbox: account.status.changed enqueued with previousStatus=ACTIVE, currentStatus=DORMANT.
        List<OutboxJpaEntity> outboxRows = findOutboxByAggregateId(account.getId());
        assertThat(outboxRows)
                .extracting(OutboxJpaEntity::getEventType)
                .contains("account.status.changed");
        OutboxJpaEntity statusEvent = outboxRows.stream()
                .filter(e -> "account.status.changed".equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertThat(statusEvent.getAggregateType()).isEqualTo("Account");
        assertThat(statusEvent.getPayload()).contains("\"previousStatus\":\"ACTIVE\"");
        assertThat(statusEvent.getPayload()).contains("\"currentStatus\":\"DORMANT\"");
        assertThat(statusEvent.getPayload()).contains("\"reasonCode\":\"DORMANT_365D\"");
        assertThat(statusEvent.getPayload()).contains("\"actorType\":\"system\"");
    }

    @Test
    @DisplayName("364일 미접속 ACTIVE 계정은 휴면 임계 미달이므로 전환되지 않는다")
    void runDormantBatch_belowThreshold_doesNotTransition() {
        String email = "dormant-fresh-" + UUID.randomUUID() + "@example.com";
        Account account = createActiveAccount(email);
        // 364 days — under the 365-day threshold.
        Instant withinWindow = Instant.now().minus(364, ChronoUnit.DAYS);
        setAccountTimestamps(account.getId(), withinWindow, withinWindow);

        scheduler.runDormantBatch();

        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // No history row created for this account.
        assertThat(historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId()))
                .isEmpty();

        // No outbox row produced for this account by the dormant batch.
        assertThat(findOutboxByAggregateId(account.getId()))
                .extracting(OutboxJpaEntity::getEventType)
                .doesNotContain("account.status.changed");
    }

    @Test
    @DisplayName("LOCKED 상태 계정은 365일 초과여도 휴면 전환 대상에서 자연 제외된다 (전이 금지 + 쿼리 status=ACTIVE 필터)")
    void runDormantBatch_lockedAccount_isExcluded() {
        String email = "dormant-locked-" + UUID.randomUUID() + "@example.com";
        Account account = createActiveAccount(email);
        Instant longAgo = Instant.now().minus(400, ChronoUnit.DAYS);
        setAccountTimestamps(account.getId(), longAgo, longAgo);

        // Lock the account first via the application use case (legitimate ACTIVE → LOCKED transition).
        accountStatusUseCase.changeStatus(new ChangeStatusCommand(
                account.getId(), AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK,
                "admin", "op-1", null));

        long historyCountBefore = historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId()).size();
        long statusEventsBefore = countStatusChangedEvents(account.getId());

        scheduler.runDormantBatch();

        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        // Stays LOCKED — the dormant query filters status='ACTIVE' so this row is never picked up,
        // and even if it were, the state machine forbids LOCKED → DORMANT (account-lifecycle.md).
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.LOCKED);

        // No additional history rows added by the dormant batch.
        assertThat(historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId()))
                .hasSize((int) historyCountBefore);

        // No additional account.status.changed outbox rows added by the dormant batch.
        assertThat(countStatusChangedEvents(account.getId())).isEqualTo(statusEventsBefore);
    }

    @Test
    @DisplayName("lastLoginSucceededAt이 NULL이고 createdAt이 366일 초과면 createdAt 기준으로 DORMANT 전환된다 (BE-103 이전 계정)")
    void runDormantBatch_nullLastLogin_oldCreatedAt_transitionsToDormant() {
        // 시나리오: BE-103 auth.login.succeeded 컨슈머가 도입되기 전 생성된 계정.
        // last_login_succeeded_at은 한 번도 채워지지 않아 NULL이고, created_at은 366일 전.
        // COALESCE(last_login_succeeded_at, created_at)이 created_at으로 폴백되어
        // 365일 임계를 초과하므로 DORMANT 전환되어야 한다 (retention.md §1.3/§1.4).
        String email = "dormant-null-login-" + UUID.randomUUID() + "@example.com";
        Account account = createActiveAccount(email);
        Instant longAgoCreated = Instant.now().minus(366, ChronoUnit.DAYS);
        setAccountTimestamps(account.getId(), longAgoCreated);

        // last_login_succeeded_at이 실제로 NULL로 저장됐는지 SQL로 직접 검증
        // (Timestamp.from(null) 같은 사고를 방지).
        Timestamp persistedLastLogin = jdbcTemplate.queryForObject(
                "SELECT last_login_succeeded_at FROM accounts WHERE id = ?",
                Timestamp.class,
                account.getId());
        assertThat(persistedLastLogin).isNull();

        scheduler.runDormantBatch();

        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.DORMANT);

        // History append: 1행, ACTIVE → DORMANT, DORMANT_365D, system.
        var history = historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(history.get(0).getToStatus()).isEqualTo(AccountStatus.DORMANT);
        assertThat(history.get(0).getReasonCode()).isEqualTo(StatusChangeReason.DORMANT_365D);
        assertThat(history.get(0).getActorType()).isEqualTo("system");
        assertThat(history.get(0).getActorId()).isNull();

        // Outbox: account.status.changed 1건 적재.
        List<OutboxJpaEntity> outboxRows = findOutboxByAggregateId(account.getId());
        assertThat(outboxRows)
                .extracting(OutboxJpaEntity::getEventType)
                .contains("account.status.changed");
        OutboxJpaEntity statusEvent = outboxRows.stream()
                .filter(e -> "account.status.changed".equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertThat(statusEvent.getAggregateType()).isEqualTo("Account");
        assertThat(statusEvent.getPayload()).contains("\"previousStatus\":\"ACTIVE\"");
        assertThat(statusEvent.getPayload()).contains("\"currentStatus\":\"DORMANT\"");
        assertThat(statusEvent.getPayload()).contains("\"reasonCode\":\"DORMANT_365D\"");
    }

    @Test
    @DisplayName("createdAt이 366일 초과여도 lastLoginSucceededAt이 364일이면 ACTIVE 유지된다 (최근 로그인 이력 보호)")
    void runDormantBatch_recentLastLogin_oldCreatedAt_remainsActive() {
        // 시나리오: 계정 자체는 366일 전 생성되었지만 364일 전 로그인 성공 이력이 있는 경우.
        // COALESCE(last_login_succeeded_at, created_at) = last_login_succeeded_at (364일 전)이
        // 365일 임계 안쪽이므로 휴면 후보에서 제외되어야 한다.
        String email = "dormant-recent-login-" + UUID.randomUUID() + "@example.com";
        Account account = createActiveAccount(email);
        Instant longAgoCreated = Instant.now().minus(366, ChronoUnit.DAYS);
        Instant recentLogin = Instant.now().minus(364, ChronoUnit.DAYS);
        setAccountTimestamps(account.getId(), longAgoCreated, recentLogin);

        scheduler.runDormantBatch();

        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // history에 추가된 행이 없어야 한다.
        assertThat(historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId()))
                .isEmpty();

        // outbox에 account.status.changed 이벤트가 적재되지 않아야 한다.
        assertThat(findOutboxByAggregateId(account.getId()))
                .extracting(OutboxJpaEntity::getEventType)
                .doesNotContain("account.status.changed");
    }

    @Test
    @DisplayName("이미 DORMANT인 계정은 배치에서 다시 처리되지 않는다 (status='ACTIVE' 필터에 의해 제외)")
    void runDormantBatch_alreadyDormant_isNotReprocessed() {
        String email = "dormant-already-" + UUID.randomUUID() + "@example.com";
        Account account = createActiveAccount(email);
        Instant longAgo = Instant.now().minus(400, ChronoUnit.DAYS);
        setAccountTimestamps(account.getId(), longAgo, longAgo);

        // First batch run: ACTIVE → DORMANT.
        scheduler.runDormantBatch();
        Account afterFirstRun = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(afterFirstRun.getStatus()).isEqualTo(AccountStatus.DORMANT);

        long historyCountAfterFirst = historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId()).size();
        long statusEventsAfterFirst = countStatusChangedEvents(account.getId());

        // Second batch run: should be a no-op for this account because it is no longer ACTIVE.
        scheduler.runDormantBatch();

        Account afterSecondRun = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(afterSecondRun.getStatus()).isEqualTo(AccountStatus.DORMANT);

        // History count unchanged — no duplicate transition row.
        assertThat(historyRepository.findByAccountIdOrderByOccurredAtDesc(account.getId()))
                .hasSize((int) historyCountAfterFirst);

        // No additional outbox rows.
        assertThat(countStatusChangedEvents(account.getId())).isEqualTo(statusEventsAfterFirst);
    }

    private Account createActiveAccount(String email) {
        Account account = Account.create(TenantId.FAN_PLATFORM, email);
        return accountRepository.save(account);
    }

    /**
     * Set {@code created_at} and {@code last_login_succeeded_at} to a fixed past instant so the
     * COALESCE-based dormant-candidate query selects this row. We must bypass the JPA entity
     * (which doesn't carry {@code last_login_succeeded_at} through {@code fromDomain}) and use
     * raw SQL.
     */
    private void setAccountTimestamps(String accountId, Instant createdAt, Instant lastLoginSucceededAt) {
        jdbcTemplate.update(
                "UPDATE accounts SET created_at = ?, last_login_succeeded_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                Timestamp.from(lastLoginSucceededAt),
                accountId);
    }

    /**
     * Overload for the BE-103-pre-migration scenario: set {@code created_at} to a fixed past
     * instant while keeping {@code last_login_succeeded_at = NULL}. Exercises the
     * {@code COALESCE(last_login_succeeded_at, created_at)} fallback branch in
     * {@link com.example.account.infrastructure.persistence.AccountJpaRepository#findActiveDormantCandidates}.
     *
     * <p>Binding {@code null} as the second parameter — JdbcTemplate maps that to SQL NULL,
     * which is what the COALESCE expression needs to fall through to {@code created_at}.
     */
    private void setAccountTimestamps(String accountId, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE accounts SET created_at = ?, last_login_succeeded_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                null,
                accountId);
    }

    private List<OutboxJpaEntity> findOutboxByAggregateId(String aggregateId) {
        return outboxRepository.findAll().stream()
                .filter(e -> aggregateId.equals(e.getAggregateId()))
                .toList();
    }

    private long countStatusChangedEvents(String aggregateId) {
        return outboxRepository.findAll().stream()
                .filter(e -> aggregateId.equals(e.getAggregateId()))
                .filter(e -> "account.status.changed".equals(e.getEventType()))
                .count();
    }
}
