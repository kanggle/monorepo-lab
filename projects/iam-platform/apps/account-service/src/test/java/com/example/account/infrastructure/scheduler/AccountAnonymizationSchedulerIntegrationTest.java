package com.example.account.infrastructure.scheduler;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AccountAnonymizationScheduler} (TASK-BE-094, fix in TASK-BE-098).
 *
 * <p>Verifies the end-to-end anonymization batch flow against a real MySQL Testcontainer:
 * <ol>
 *   <li>30일 초과 DELETED + masked_at IS NULL → 배치 → masked_at 설정, PII 마스킹,
 *       outbox에 {@code account.deleted (anonymized=true)} 적재</li>
 *   <li>29일 DELETED + masked_at IS NULL → 배치 → 처리 없음 (유예 기간 내)</li>
 *   <li>DELETED + masked_at 이미 설정 → 배치 → 중복 처리 없음 (멱등성 가드)</li>
 *   <li>30일 초과지만 grace period 복구 (status=ACTIVE) → 배치 → 처리 없음
 *       (쿼리 status='DELETED' 필터에 의해 자연 제외)</li>
 * </ol>
 *
 * <p>Outbox is queried directly because the spec mandates outbox as the source of truth
 * for event publication. Kafka template + outbox poller are mocked to avoid producer
 * metadata lookup at context startup (matches AccountSignupIntegrationTest pattern).
 *
 * <p>TASK-BE-098: extends {@link AbstractIntegrationTest} so MySQL/Kafka containers are
 * shared per-JVM (platform/testing-strategy.md "Container Lifecycle"). The internal API
 * token property is registered via a subclass {@code @DynamicPropertySource}.
 *
 * <p><b>TASK-BE-498 — timestamp fixtures must not use {@code java.sql.Timestamp}.</b> This class
 * seeds {@code deleted_at} / {@code masked_at} through a raw {@code JdbcTemplate} but reads them
 * back through the application's Hibernate {@code Instant} mapping. Those two paths only agree if
 * the fixture writes the <b>UTC wall clock</b> — see {@link #utcWallClock(Instant)}. Writing a
 * {@code Timestamp} instead makes the driver format it in the JVM's default timezone, and the
 * suite then fails by exactly the host's UTC offset (it did: 9h on a KST machine, invisible on
 * CI's UTC runners).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("AccountAnonymizationScheduler 통합 테스트 — PII 익명화 배치 + outbox 발행")
class AccountAnonymizationSchedulerIntegrationTest extends AbstractIntegrationTest {

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
    }

    @Autowired
    private AccountAnonymizationScheduler scheduler;

    @Autowired
    private AccountStatusUseCase accountStatusUseCase;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AccountOutboxJpaRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @MockitoBean
    private AccountOutboxPublisher accountOutboxPublisher;

    @Test
    @DisplayName("30일 초과 DELETED + masked_at NULL — 배치 실행 후 PII 마스킹, masked_at 설정, account.deleted(anonymized=true) 발행")
    void runAnonymizationBatch_eligibleDeletedAccount_anonymizesAndPublishesEvent() {
        String email = "anonymize-eligible-" + UUID.randomUUID() + "@example.com";
        Account account = createDeletedAccountWithProfile(email);
        // Push deleted_at 31 days into the past (beyond the 30-day grace period).
        setDeletedAt(account.getId(), Instant.now().minus(31, ChronoUnit.DAYS));

        long outboxRowsBefore = countOutboxByAggregate(account.getId());

        scheduler.runAnonymizationBatch();

        // accounts.email rewritten to anon_<sha256[:12]>@deleted.local; email_hash full hex.
        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(reloaded.getEmail()).startsWith("anon_");
        assertThat(reloaded.getEmail()).endsWith("@deleted.local");
        assertThat(reloaded.getEmail()).hasSize("anon_".length() + 12 + "@deleted.local".length());
        assertThat(reloaded.getEmailHash()).hasSize(64).matches("[0-9a-f]{64}");

        // Profile masked + masked_at stamped (retention.md §2.5):
        //   display_name → '탈퇴한 사용자' 고정 문자열
        //   phone_number, birth_date, preferences → NULL
        Profile profile = profileRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(profile.getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(profile.getPhoneNumber()).isNull();
        assertThat(profile.getBirthDate()).isNull();
        assertThat(profile.getPreferences()).isNull();
        assertThat(profile.getMaskedAt()).isNotNull();

        // Outbox: a new account.deleted row with anonymized=true was appended by this batch.
        long outboxRowsAfter = countOutboxByAggregate(account.getId());
        assertThat(outboxRowsAfter).isGreaterThan(outboxRowsBefore);

        AccountOutboxJpaEntity anonymizedEvent = findOutboxByAggregate(account.getId()).stream()
                .filter(e -> "account.deleted".equals(e.getEventType()))
                .filter(e -> e.getPayload().contains("\"anonymized\":true"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected an account.deleted outbox row with anonymized=true"));
        assertThat(anonymizedEvent.getAggregateType()).isEqualTo("Account");
        assertThat(anonymizedEvent.getPayload()).contains("\"actorType\":\"system\"");
        // TASK-BE-097: payload must carry gracePeriodEndsAt (account-events.md schema)
        // and reasonCode resolved from the original DELETED transition (USER_REQUEST in the
        // helper) — not the previously hardcoded value.
        assertThat(anonymizedEvent.getPayload()).contains("\"gracePeriodEndsAt\"");
        assertThat(anonymizedEvent.getPayload()).contains("\"reasonCode\":\"USER_REQUEST\"");
    }

    @Test
    @DisplayName("29일 DELETED + masked_at NULL — 유예 기간 내이므로 익명화되지 않는다")
    void runAnonymizationBatch_belowGracePeriod_doesNotAnonymize() {
        String email = "anonymize-fresh-" + UUID.randomUUID() + "@example.com";
        Account account = createDeletedAccountWithProfile(email);
        // 29 days — under the 30-day grace period.
        setDeletedAt(account.getId(), Instant.now().minus(29, ChronoUnit.DAYS));

        long anonymizedEventsBefore = countAnonymizedDeletedEvents(account.getId());

        scheduler.runAnonymizationBatch();

        // Email + profile remain untouched.
        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(email);
        assertThat(reloaded.getEmailHash()).isNull();

        Profile profile = profileRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(profile.getDisplayName()).isEqualTo("John Doe");
        assertThat(profile.getMaskedAt()).isNull();

        // No new account.deleted (anonymized=true) row in outbox.
        assertThat(countAnonymizedDeletedEvents(account.getId())).isEqualTo(anonymizedEventsBefore);
    }

    @Test
    @DisplayName("DELETED + masked_at 이미 설정됨 — 멱등성 가드에 의해 중복 처리되지 않는다")
    void runAnonymizationBatch_alreadyAnonymized_isNotReprocessed() {
        String email = "anonymize-done-" + UUID.randomUUID() + "@example.com";
        Account account = createDeletedAccountWithProfile(email);
        setDeletedAt(account.getId(), Instant.now().minus(60, ChronoUnit.DAYS));

        // Pre-mark the profile as already anonymized (simulates a prior run completing).
        // Truncated to MICROS up front: MySQL DATETIME(6) cannot store Instant.now()'s nanos, so
        // rounding here (rather than loosening the assertion later) keeps the round-trip EXACT —
        // a 9h timezone skew must fail, and it cannot hide behind a tolerant comparison.
        Instant priorMaskedAt = Instant.now().minus(10, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update(
                "UPDATE profiles SET masked_at = ?, display_name = NULL, phone_number = NULL, birth_date = NULL "
                        + "WHERE account_id = ?",
                utcWallClock(priorMaskedAt),
                account.getId());
        // Also rewrite the email to a unique anonymized-form value so we can detect any second-pass
        // corruption. The 12-char prefix is hex chars from the account UUID for per-test uniqueness
        // (the email column has a UNIQUE constraint).
        String hexFromId = account.getId().replace("-", "").substring(0, 12);
        String preAnonEmail = "anon_" + hexFromId + "@deleted.local";
        String preAnonHash = "a".repeat(64);
        jdbcTemplate.update(
                "UPDATE accounts SET email = ?, email_hash = ? WHERE id = ?",
                preAnonEmail, preAnonHash, account.getId());

        long anonymizedEventsBefore = countAnonymizedDeletedEvents(account.getId());

        scheduler.runAnonymizationBatch();

        // Email and email_hash unchanged — no second masking pass corrupted the values.
        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(preAnonEmail);
        assertThat(reloaded.getEmailHash()).isEqualTo(preAnonHash);

        Profile profile = profileRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(profile.getMaskedAt()).isNotNull();
        // masked_at must NOT be advanced — it is the very instant the prior run stamped.
        //
        // TASK-BE-498: this is an EXACT equality, deliberately. It carries two proofs at once:
        //   1. the idempotence guard held (a second pass did not re-stamp), and
        //   2. the fixture's write convention round-trips through the application's read path
        //      without a timezone shift — the host-timezone bug this assertion used to hide.
        // Widening it (isCloseTo, truncating to HOURS/DAYS) would let a re-stamp OR a timezone
        // skew slip through. The value was truncated to MySQL's DATETIME(6) precision at write
        // time so no tolerance is needed here.
        assertThat(profile.getMaskedAt()).isEqualTo(priorMaskedAt);

        // No additional account.deleted (anonymized=true) outbox row.
        assertThat(countAnonymizedDeletedEvents(account.getId())).isEqualTo(anonymizedEventsBefore);
    }

    @Test
    @DisplayName("30일 초과지만 grace period 복구된 ACTIVE 계정 — 쿼리 status='DELETED' 필터로 자연 제외")
    void runAnonymizationBatch_recoveredAccount_isExcluded() {
        String email = "anonymize-recovered-" + UUID.randomUUID() + "@example.com";
        Account account = createDeletedAccountWithProfile(email);
        setDeletedAt(account.getId(), Instant.now().minus(45, ChronoUnit.DAYS));

        // Recover within grace period (DELETED → ACTIVE via WITHIN_GRACE_PERIOD reason).
        accountStatusUseCase.changeStatus(new ChangeStatusCommand(
                account.getId(), AccountStatus.ACTIVE, StatusChangeReason.WITHIN_GRACE_PERIOD,
                "admin", "op-1", null));

        Account beforeBatch = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        // Sanity: account is back to ACTIVE and deleted_at was cleared by Account.changeStatus().
        assertThat(beforeBatch.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(beforeBatch.getDeletedAt()).isNull();

        long anonymizedEventsBefore = countAnonymizedDeletedEvents(account.getId());

        scheduler.runAnonymizationBatch();

        // PII intact: email unchanged, hash still NULL, profile fields preserved.
        Account reloaded = accountRepository.findById(TenantId.FAN_PLATFORM, account.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(email);
        assertThat(reloaded.getEmailHash()).isNull();

        Profile profile = profileRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(profile.getDisplayName()).isEqualTo("John Doe");
        assertThat(profile.getMaskedAt()).isNull();

        // No new anonymized event published.
        assertThat(countAnonymizedDeletedEvents(account.getId())).isEqualTo(anonymizedEventsBefore);
    }

    /**
     * Build a DELETED account with a fully populated profile. Uses the application use case
     * for the ACTIVE → DELETED transition so the row reaches DELETED through the legitimate
     * state-machine path (history + initial outbox event included).
     */
    private Account createDeletedAccountWithProfile(String email) {
        Account account = Account.create(TenantId.FAN_PLATFORM, email);
        Account saved = accountRepository.save(account);

        Profile profile = Profile.create(saved.getId(), "John Doe", "ko-KR", "Asia/Seoul");
        profile.update("John Doe", "+82-10-1234-5678", LocalDate.of(1990, 1, 15),
                "ko-KR", "Asia/Seoul", null);
        profileRepository.save(profile);

        accountStatusUseCase.deleteAccount(saved.getId(),
                StatusChangeReason.USER_REQUEST, "user", saved.getId());
        return accountRepository.findById(TenantId.FAN_PLATFORM, saved.getId()).orElseThrow();
    }

    /** Push {@code deleted_at} to a fixed past instant so the anonymization-candidate query matches. */
    private void setDeletedAt(String accountId, Instant deletedAt) {
        jdbcTemplate.update(
                "UPDATE accounts SET deleted_at = ? WHERE id = ?",
                utcWallClock(deletedAt),
                accountId);
    }

    /**
     * TASK-BE-498: binds an {@link Instant} to a MySQL {@code DATETIME} column using the SAME
     * convention Hibernate reads it back with — the UTC wall clock.
     *
     * <p><b>Why not {@code java.sql.Timestamp.from(instant)}.</b> That is what this class used
     * to do, and it made the suite fail on any host whose JVM default timezone is not UTC.
     * The two paths disagreed:
     *
     * <ul>
     *   <li><b>write</b> (this fixture only): Connector/J formats a {@code Timestamp} using the
     *       <b>JVM default timezone</b>, so on a KST host the naive {@code DATETIME} stored is
     *       the wall clock {@code instant + 9h};</li>
     *   <li><b>read</b> (the same path the application uses): Hibernate maps the naive
     *       {@code DATETIME} to an {@code Instant} by interpreting it as <b>UTC</b>.</li>
     * </ul>
     *
     * <p>So the value read back was the value written plus the host's UTC offset — exactly the
     * 9h skew observed. On CI (Linux, UTC) the offset is 0, which is why it stayed green there
     * and red only on a developer machine.
     *
     * <p>A {@code LocalDateTime} is written literally by the driver (no timezone conversion),
     * so storing {@code LocalDateTime.ofInstant(instant, UTC)} puts the UTC wall clock in the
     * column and the Hibernate read reconstructs the original {@code Instant} exactly — on any
     * host. The round-trip is asserted, not assumed: see
     * {@link #runAnonymizationBatch_alreadyAnonymized_isNotReprocessed()}.
     *
     * <p>The application itself never had this bug — it holds no {@code JdbcTemplate} and no
     * {@code java.sql.Timestamp}; every timestamp goes through the JPA {@code Instant} mapping,
     * and the batch binds its threshold to a JPQL (not native) query. The mismatch existed only
     * between this test's write and its read.
     */
    private static LocalDateTime utcWallClock(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private List<AccountOutboxJpaEntity> findOutboxByAggregate(String aggregateId) {
        return outboxRepository.findAll().stream()
                .filter(e -> aggregateId.equals(e.getAggregateId()))
                .toList();
    }

    private long countOutboxByAggregate(String aggregateId) {
        return findOutboxByAggregate(aggregateId).size();
    }

    private long countAnonymizedDeletedEvents(String aggregateId) {
        return findOutboxByAggregate(aggregateId).stream()
                .filter(e -> "account.deleted".equals(e.getEventType()))
                .filter(e -> e.getPayload().contains("\"anonymized\":true"))
                .count();
    }
}
