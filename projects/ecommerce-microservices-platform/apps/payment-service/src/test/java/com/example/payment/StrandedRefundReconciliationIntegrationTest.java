package com.example.payment;

import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentGatewayStatus;
import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.application.service.StrandedRefundSweeper;
import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.model.StrandedRefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Reconciliation IT for the stranded-refund sweeper (TASK-BE-438, AC-2 / AC-3 / AC-5) against a
 * real Postgres (Flyway V7 applied) and a stubbed PG gateway.
 *
 * <p><b>Compile-only locally</b> (Testcontainers Docker blocker on this Windows host; see memory
 * {@code project_testcontainers_docker_desktop_blocker}) and there is no ecommerce
 * {@code @Tag("integration")} CI lane yet (TASK-MONO-307). The unit tests
 * ({@code StrandedRefundReconcilerTest} / {@code StrandedRefundSweeperTest}) are the CI gate; this
 * IT documents the end-to-end (DB + Flyway + sweeper/reconciler wiring) proof for when the lane exists.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "outbox.polling.enabled=false",
        // Drive the sweeper explicitly via sweepOnce(); disable the background scheduler.
        "payment.stranded-refund.fixed-delay-ms=3600000",
        "payment.stranded-refund.initial-delay-ms=3600000",
        "payment.stranded-refund.max-attempts=3"
})
@Tag("integration")
@Testcontainers
@DisplayName("StrandedRefund 자동복구 sweeper 통합 테스트 (TASK-BE-438)")
class StrandedRefundReconciliationIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("payment_user")
            .withPassword("payment_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private StrandedRefundSweeper sweeper;

    @Autowired
    private StrandedRefundRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PaymentGatewayPort paymentGateway;

    private StrandedRefund seedStranded(String paymentId) {
        return repository.save(StrandedRefund.open(
                paymentId, "order-" + paymentId, "pk_" + paymentId, 30000L,
                "PgGatewayUnavailableException", Instant.now().minusSeconds(1)));
    }

    @Test
    @DisplayName("AC-2: PG 가 이미 CANCELED 면 cancelPayment 없이 RESOLVED 로 자동복구된다")
    void sweep_pgAlreadyCanceled_resolvesWithoutReCancel() {
        StrandedRefund seeded = seedStranded("ac2-" + System.nanoTime());
        given(paymentGateway.fetchStatus(anyString())).willReturn(PaymentGatewayStatus.CANCELED);

        sweeper.sweepOnce();

        StrandedRefund reloaded = repository.findById(seeded.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StrandedRefundStatus.RESOLVED);
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
    }

    @Test
    @DisplayName("AC-3: PG 가 CAPTURED 면 cancelPayment 후 RESOLVED 로 자동복구된다")
    void sweep_captured_cancelSucceeds_resolves() {
        StrandedRefund seeded = seedStranded("ac3-" + System.nanoTime());
        given(paymentGateway.fetchStatus(anyString())).willReturn(PaymentGatewayStatus.CAPTURED);

        sweeper.sweepOnce();

        StrandedRefund reloaded = repository.findById(seeded.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StrandedRefundStatus.RESOLVED);
        verify(paymentGateway).cancelPayment(anyString(), anyString());
    }

    @Test
    @DisplayName("AC-5: 확정적 4xx 거부 시 UNRESOLVED 로 종료되고 PaymentRefundUnresolved outbox row 가 co-commit 된다")
    void sweep_definitiveRejection_terminatesUnresolvedAndEscalates() {
        String paymentId = "ac5-" + System.nanoTime();
        StrandedRefund seeded = seedStranded(paymentId);
        given(paymentGateway.fetchStatus(anyString())).willReturn(PaymentGatewayStatus.CAPTURED);
        doThrow(new com.example.payment.application.exception.PgConfirmFailedException("rejected"))
                .when(paymentGateway).cancelPayment(anyString(), anyString());

        sweeper.sweepOnce();

        StrandedRefund reloaded = repository.findById(seeded.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StrandedRefundStatus.UNRESOLVED);

        List<Map<String, Object>> unresolved = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentRefundUnresolved' AND payload LIKE ?",
                "%" + paymentId + "%");
        assertThat(unresolved).as("terminal escalation must co-commit with the UNRESOLVED transition").hasSize(1);
    }
}
