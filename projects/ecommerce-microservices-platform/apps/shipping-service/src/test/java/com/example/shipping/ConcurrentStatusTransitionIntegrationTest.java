package com.example.shipping;

import com.example.shipping.application.command.UpdateShippingStatusCommand;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * TASK-BE-537 — `PUT /api/shippings/{id}/status` 동시 전이 재현 조사.
 *
 * <p><b>가설</b>: {@code ShippingStatus.canTransitionTo} 상태 기계는 <i>순차</i> 재생만 막는다
 * (TASK-BE-538 인구조사에서 확인됨). {@code shipping-service} 전역에 {@code @Version}
 * 낙관적 락이 없으므로(재측정: 이 서비스 0건 / 저장소 나머지 103개 파일에는 존재 —
 * 패턴 자체는 유효), 두 개의 동시 {@code SHIPPED} 전이가 <b>둘 다</b> 인메모리 가드를
 * 통과하고 각각 {@code ManualShipConfirmRequested} 를 발행할 수 있다.
 *
 * <h2>진짜 겹침을 어떻게 강제했는가 (F2 대응)</h2>
 *
 * <p>"동시"를 주장하는 테스트가 실제로는 직렬 실행되어 초록이 되는 것이 이 조사에서 가장
 * 위험한 실패 모드다. 여기서는 <b>시간에 의존하지 않는 결정론적 인터리빙</b>을 쓴다:
 *
 * <ol>
 *   <li>T1 이 자기 트랜잭션을 열고 {@code updateStatus} 를 수행한 뒤, 커밋하지 <b>않고</b>
 *       {@code t2PassedGuard} 래치에서 대기한다.</li>
 *   <li>T2 는 이미 주입되어 있는 {@link Clock} 심(seam)에 일회성 훅을 걸고 자기 트랜잭션을
 *       연다. {@code Shipping.transitionTo} 는 {@code canTransitionTo} 가 통과한 <b>다음에야</b>
 *       {@code Instant.now(clock)} 를 호출하므로(Shipping.java:87 검사 → :104 시계 호출),
 *       훅이 발화하는 시점은 곧 "T2 가 도메인 가드를 통과했다"는 뜻이다. 훅은 래치를 내린다.</li>
 *   <li>따라서 래치가 내려간 사실 자체가 <b>T1 의 트랜잭션이 열린 채 커밋되지 않은 동안
 *       T2 가 PREPARING 을 읽고 상태 기계를 통과했다</b>는 증거다. 두 트랜잭션이 직렬화됐다면
 *       래치는 절대 내려가지 않고 이 테스트는 타임아웃으로 <b>실패</b>한다.</li>
 *   <li>래치 해제 후 T1 이 커밋하고, T2 의 UPDATE flush 는 T1 의 행 잠금을 기다렸다가
 *       진행한다. 교착은 구조적으로 불가능하다 — T1 은 오직 래치만 기다리고, 그 래치는
 *       T2 가 <i>차단될 수 있는 어떤 작업보다도 먼저</i> 내린다.</li>
 * </ol>
 *
 * <p>이 인터리빙은 <b>인위적으로 만들어낸 불가능한 입력이 아니라</b>, 프로덕션에서
 * 드물게 발생하는 창(window)을 결정론적으로 재현한 것이다. 두 HTTP 요청이 각자
 * 트랜잭션을 열면 read-A / read-B / commit-A / commit-B 순서는 그대로 성립한다.
 * 다만 이 테스트는 창이 <b>존재한다</b>는 것과 창에 들어갔을 때의 <b>결과</b>를 증명할 뿐,
 * 프로덕션에서 얼마나 <b>자주</b> 들어가는지는 측정하지 않는다.
 *
 * <h2>가드가 물 수 있는가 (bite) — TASK-BE-547 로 채택된 매개자</h2>
 *
 * <p>이 테스트는 TASK-BE-537 에서 결함을 <b>특성화</b>(발행 2건)했고, TASK-BE-547 이 그
 * 결함을 고치면서 이제 <b>회귀 가드</b>가 됐다. 채택된 가드는 {@code @Version}(root 경합
 * 제거)이 아니라 <b>발행 측 결정적 event_id 채번</b>이다: {@code ShippingStatusChanged} 의
 * {@code event_id} 를 {@code (shippingId, newStatus)} 에서 결정적으로 유도하고, 그 id 가
 * {@code shipping_outbox} 행 PK 이기도 하므로 <b>두 번째 동시 발행의 아웃박스 INSERT 가 PK
 * 에서 충돌</b>한다. 그 트랜잭션 전체가 롤백되어 {@code ShippingStatusChanged} 도
 * {@code ManualShipConfirmRequested} 도 1건씩만 남고, 패배 스레드는
 * {@code DataIntegrityViolationException}(SQLSTATE 23505) 으로 튕겨 실제 HTTP 경로에서는
 * {@code GlobalExceptionHandler} 의 DIVE 백스톱(TASK-BE-542)을 통해 {@code 409} 로 매핑된다.
 *
 * <p>따라서 매개자는 read-then-write 애플리케이션 검사가 아니라 <b>DB 가 강제하는 아웃박스
 * PK 유니크 제약</b>이다 — 동시성에서도 성립한다(AC-4). {@code @Version} 을 쓰지 않으므로 root
 * 이중 UPDATE(둘 다 status=SHIPPED, 같은 값이라 무해)는 남지만, 티켓이 방어하는 <b>피해</b>
 * (중복 이벤트 → 중복 알림, 그리고 덤으로 중복 {@code ManualShipConfirmRequested})는 사라진다.
 * root 경합 자체를 없애려면 {@code @Version} + 409 계약(별 티켓)이 필요하다.
 *
 * <p><b>권위</b>: 로컬 Windows Testcontainers 는 이 저장소에서 FLAKY 하므로 권위가 아니다.
 * CI Linux 가 권위다.
 */
@SpringBootTest(
        classes = ShippingServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@Import(ConcurrentStatusTransitionIntegrationTest.HookClockConfig.class)
@DisplayName("동시 상태 전이 (TASK-BE-537 특성화 → TASK-BE-547 회귀 가드) — PUT /shippings/{id}/status")
class ConcurrentStatusTransitionIntegrationTest {

    private static final String ROLE_OPERATOR = "ECOMMERCE_OPERATOR";
    private static final long LATCH_TIMEOUT_SECONDS = 20;

    /**
     * 스레드별 일회성 {@link Clock} 훅. {@code null} 이 아니면 다음 {@code clock.instant()}
     * 호출 때 한 번 실행되고 즉시 해제된다. 이 테스트 밖의 모든 스레드에서는 no-op 이므로
     * 프로덕션 경로의 시계 의미는 바뀌지 않는다.
     */
    private static final ThreadLocal<AtomicReference<Runnable>> CLOCK_HOOK =
            ThreadLocal.withInitial(() -> new AtomicReference<>(null));

    private static void armClockHook(Runnable hook) {
        CLOCK_HOOK.get().set(hook);
    }

    @TestConfiguration
    static class HookClockConfig {

        /**
         * 프로덕션 {@code ClockConfig} 의 UTC 시스템 시계와 의미가 동일하되, 스레드에 훅이
         * 걸려 있으면 한 번 발화시킨다. 시각 자체는 조작하지 않는다.
         *
         * <p>빈 이름을 {@code clock} 이 아닌 {@code be537HookClock} 으로 둔 것은 의도적이다 —
         * 같은 이름이면 {@code ClockConfig#clock} 과 정의 충돌
         * ({@code BeanDefinitionOverrideException})이 난다. 이름을 달리하고 {@link Primary}
         * 로 주입 우선순위만 가져간다.
         */
        @Bean
        @Primary
        Clock be537HookClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    Runnable hook = CLOCK_HOOK.get().getAndSet(null);
                    if (hook != null) {
                        hook.run();
                    }
                    return Instant.now();
                }
            };
        }
    }

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shipping_db")
            .withUsername("shipping_user")
            .withPassword("shipping_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ShippingCommandService commandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** wms 로 라우팅된 PREPARING 배송 1건을 시드하고 shippingId 를 돌려준다. */
    private String seedWmsRoutedPreparing(String orderId) {
        String shippingId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO shippings (shipping_id, tenant_id, order_id, user_id, status, "
                        + "wms_routed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'PREPARING', true, ?, ?)",
                shippingId, TenantContext.DEFAULT_TENANT_ID, orderId, "user-" + orderId,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return shippingId;
    }

    private UpdateShippingStatusCommand shipCommand(String shippingId) {
        return new UpdateShippingStatusCommand(
                shippingId, ShippingStatus.SHIPPED, "TRACK-1", "CJ-LOGISTICS", true, ROLE_OPERATOR);
    }

    private int manualConfirmRowCount(String orderId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_outbox "
                        + "WHERE aggregate_id = ? AND event_type = 'ManualShipConfirmRequested'",
                Integer.class, orderId);
        return n == null ? 0 : n;
    }

    /** ShippingStatusChanged 아웃박스 행 수 — aggregate_id = shippingId (이 이벤트의 BE-547 피해 지표). */
    private int statusChangedRowCount(String shippingId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_outbox "
                        + "WHERE aggregate_id = ? AND event_type = 'ShippingStatusChanged'",
                Integer.class, shippingId);
        return n == null ? 0 : n;
    }

    /** 예외 원인 사슬에 유니크 위반(SQLSTATE 23505 또는 DataIntegrityViolationException)이 있는가. */
    private static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
            if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // AC-1 — 유효 격리 수준을 "추론"이 아니라 "측정"으로 확정한다.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("AC-1: updateStatus 가 실제로 도는 트랜잭션의 격리 수준은 read committed (측정값)")
    void effectiveIsolationLevel_isReadCommitted() {
        // shipping-service 어디에도 격리 수준 설정이 없다(application.yml 에 isolation 키 없음,
        // @Transactional(isolation=...) 0건, Hikari transaction-isolation 미설정) => Postgres
        // 서버 기본값이 그대로 유효 격리 수준이 된다. 그 "기본값이 무엇인지"를 추론하지 않고
        // 서비스가 실제로 쓰는 트랜잭션 안에서 직접 물어본다.
        String isolation = transactionTemplate.execute(status ->
                jdbcTemplate.queryForObject("SHOW transaction_isolation", String.class));

        assertThat(isolation).isEqualTo("read committed");
    }

    // ---------------------------------------------------------------------
    // 대조군 — 상태 기계가 "순차" 재생은 실제로 막는다는 것을 같은 하네스에서 확인.
    // (이게 없으면 아래 동시 테스트의 2건이 "가드가 아예 없어서"인지
    //  "동시성 때문"인지 구분되지 않는다.)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("대조군(순차): 두 번째 SHIPPED 는 상태 기계가 거부 → 발행 1건")
    void sequentialReplay_isRejectedByStateMachine_singlePublish() {
        String orderId = "ord-seq-" + System.nanoTime();
        String shippingId = seedWmsRoutedPreparing(orderId);

        commandService.updateStatus(shipCommand(shippingId));

        try {
            commandService.updateStatus(shipCommand(shippingId));
            fail("두 번째 순차 SHIPPED 는 InvalidStatusTransitionException 이어야 한다");
        } catch (RuntimeException expected) {
            assertThat(expected.getClass().getSimpleName())
                    .isEqualTo("InvalidStatusTransitionException");
        }

        assertThat(manualConfirmRowCount(orderId))
                .as("순차 재생은 상태 기계가 막으므로 발행은 1건")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("대조군(정상 전진): PREPARING -> SHIPPED -> IN_TRANSIT 는 그대로 동작 (AC-4)")
    void legitimateForwardTransitions_stillWork() {
        String orderId = "ord-fwd-" + System.nanoTime();
        String shippingId = seedWmsRoutedPreparing(orderId);

        commandService.updateStatus(shipCommand(shippingId));
        commandService.updateStatus(new UpdateShippingStatusCommand(
                shippingId, ShippingStatus.IN_TRANSIT, "TRACK-1", "CJ-LOGISTICS", false, ROLE_OPERATOR));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM shippings WHERE shipping_id = ?", String.class, shippingId);
        assertThat(status).isEqualTo("IN_TRANSIT");
        assertThat(manualConfirmRowCount(orderId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // AC-2 / AC-3 — 재현 시도 본체.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("AC-2/AC-4 (BE-547): 동시 SHIPPED 두 건이 겹쳐도 발행은 정확히 1건 (아웃박스 PK 가 매개)")
    void concurrentShippedTransitions_deterministicEventId_collapsesToSinglePublish() throws Exception {
        String orderId = "ord-cc-" + System.nanoTime();
        String shippingId = seedWmsRoutedPreparing(orderId);

        // T2 가 도메인 가드를 통과한 순간 내려가는 래치. 내려갔다는 사실 = 진짜 겹침의 증거.
        CountDownLatch t2PassedGuard = new CountDownLatch(1);
        AtomicReference<Throwable> t1Error = new AtomicReference<>();
        AtomicReference<Throwable> t2Error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 자기 트랜잭션 안에서 읽고 → 가드 통과 → 아웃박스 기록. 아직 커밋 안 함.
                    commandService.updateStatus(shipCommand(shippingId));
                    try {
                        // T2 도 PREPARING 을 읽고 가드를 통과할 때까지 커밋을 미룬다.
                        if (!t2PassedGuard.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "T2 가 제한시간 내에 도메인 가드를 통과하지 못했다 — "
                                            + "두 트랜잭션이 직렬화됐다는 뜻(= 재현 실패, 유효한 결과)");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
            } catch (Throwable e) {
                t1Error.set(e);
                t2PassedGuard.countDown(); // T2 를 영원히 매달아두지 않는다.
            }
        }, "be537-t1");

        Thread t2 = new Thread(() -> {
            try {
                // Shipping.transitionTo 는 canTransitionTo 통과 후에야 시계를 부른다
                // (Shipping.java:87 검사 -> :104 Instant.now(clock)). 따라서 이 훅의 발화 =
                // "T2 가 PREPARING 을 읽고 상태 기계를 통과했다".
                armClockHook(t2PassedGuard::countDown);
                transactionTemplate.executeWithoutResult(status ->
                        commandService.updateStatus(shipCommand(shippingId)));
            } catch (Throwable e) {
                t2Error.set(e);
            } finally {
                t2PassedGuard.countDown(); // T2 가 가드에서 튕겨도 T1 은 풀어준다.
                CLOCK_HOOK.remove();
            }
        }, "be537-t2");

        t1.start();
        // T1 이 먼저 읽고 아웃박스를 쓸 시간을 준 뒤 T2 를 띄운다. 이 sleep 은 타이밍 단언이
        // 아니라 출발 순서 조정용일 뿐 — 결정론은 sleep 이 아니라 래치가 보장한다.
        Thread.sleep(300);
        t2.start();

        t1.join(TimeUnit.SECONDS.toMillis(60));
        t2.join(TimeUnit.SECONDS.toMillis(60));

        assertThat(t1.isAlive()).as("T1 이 종료되지 않음").isFalse();
        assertThat(t2.isAlive()).as("T2 가 종료되지 않음").isFalse();

        int statusChanged = statusChangedRowCount(shippingId);
        int manualConfirm = manualConfirmRowCount(orderId);
        String finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM shippings WHERE shipping_id = ?", String.class, shippingId);

        // 진단용 출력: 무엇이 관측됐는지 남긴다.
        System.out.printf(
                "[TASK-BE-547] ShippingStatusChanged 발행=%d, ManualShipConfirmRequested 발행=%d, "
                        + "최종 status=%s, t1Error=%s, t2Error=%s%n",
                statusChanged, manualConfirm, finalStatus,
                t1Error.get() == null ? "none" : t1Error.get().toString(),
                t2Error.get() == null ? "none" : t2Error.get().toString());

        // ── 매개자 검증 (AC-4): 정확히 한 스레드가 커밋하고, 다른 하나는 아웃박스 PK
        //    유니크 위반(23505)으로 롤백됐다. 어느 스레드가 이기는지는 커밋 경합 타이밍에
        //    달려 있으므로 특정 스레드를 승자로 못박지 않는다 — XOR 로만 단언한다.
        boolean t1Failed = t1Error.get() != null;
        boolean t2Failed = t2Error.get() != null;
        assertThat(t1Failed ^ t2Failed)
                .as("동시 이중 SHIPPED 중 정확히 한 트랜잭션만 실패해야 한다 "
                        + "(t1Error=%s, t2Error=%s)", t1Error.get(), t2Error.get())
                .isTrue();
        Throwable loser = t1Failed ? t1Error.get() : t2Error.get();
        assertThat(isUniqueViolation(loser))
                .as("패배 트랜잭션은 아웃박스 PK 유니크 위반(23505/DataIntegrityViolation)이어야 한다: %s", loser)
                .isTrue();

        // ── 부수효과 검증 (AC-2): 결정적 event_id = 아웃박스 PK 덕분에 동시 이중 전이가
        //    남기는 이벤트는 각 유형당 정확히 1건이다. ShippingStatusChanged 1건 = 고객
        //    알림이 (notification 의 event_id dedup 을 통해) 한 번만 발생함을 발행 원천에서
        //    보장한다. ManualShipConfirmRequested 1건 = wms 이중 차감 위험도 함께 접힌다.
        assertThat(statusChanged)
                .as("동시 SHIPPED 전이가 남긴 ShippingStatusChanged 아웃박스 행 수 "
                        + "(1 = BE-547 가드가 중복 이벤트를 발행 원천에서 접음 / 2 = 회귀)")
                .isEqualTo(1);
        assertThat(manualConfirm)
                .as("동시 SHIPPED 전이가 남긴 ManualShipConfirmRequested 아웃박스 행 수 "
                        + "(1 = 패배 트랜잭션 전체 롤백으로 함께 접힘 / 2 = 회귀)")
                .isEqualTo(1);
        assertThat(finalStatus).as("최종 상태는 SHIPPED").isEqualTo("SHIPPED");
    }
}
