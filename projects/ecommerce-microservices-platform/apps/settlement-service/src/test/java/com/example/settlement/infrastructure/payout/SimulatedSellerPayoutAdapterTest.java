package com.example.settlement.infrastructure.payout;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.settlement.application.port.SellerPayoutPort.PayoutExecutionResult;
import com.example.settlement.domain.payout.SellerPayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SimulatedSellerPayoutAdapter} (TASK-BE-416 AC-1):
 * <ul>
 *   <li>returns PAID outcome with a {@code SIM-} reference;</li>
 *   <li>log entry contains "simulated payout (NOT a real disbursement)" — no
 *       green-washing;</li>
 *   <li>reference includes periodId + sellerId for traceability.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SimulatedSellerPayoutAdapterTest {

    private SimulatedSellerPayoutAdapter adapter;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        adapter = new SimulatedSellerPayoutAdapter();
        logger = (Logger) LoggerFactory.getLogger(SimulatedSellerPayoutAdapter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    private static SellerPayout pendingPayout(String payoutId, String periodId, String sellerId) {
        return SellerPayout.pending(payoutId, periodId, "tenantA", sellerId, 27_000L, 3_000L, 1);
    }

    @Test
    void execute_returns_paid_outcome() {
        SellerPayout payout = pendingPayout("po-1", "period-1", "seller-1");

        PayoutExecutionResult result = adapter.execute(payout);

        assertThat(result.isPaid()).isTrue();
        assertThat(result.status()).isEqualTo(PayoutExecutionResult.OutcomeStatus.PAID);
        assertThat(result.reference()).isNotBlank();
    }

    @Test
    void execute_reference_has_SIM_prefix() {
        SellerPayout payout = pendingPayout("po-2", "period-42", "seller-99");

        PayoutExecutionResult result = adapter.execute(payout);

        // SIM- prefix makes simulation self-evident at a glance.
        assertThat(result.reference()).startsWith("SIM-");
    }

    @Test
    void execute_reference_contains_periodId_and_sellerId() {
        SellerPayout payout = pendingPayout("po-3", "period-xyz", "seller-abc");

        PayoutExecutionResult result = adapter.execute(payout);

        // Reference format: SIM-{periodId}-{sellerId}-{uuid} — uniquely traceable.
        assertThat(result.reference()).contains("period-xyz");
        assertThat(result.reference()).contains("seller-abc");
    }

    @Test
    void execute_logs_no_green_wash_message() {
        SellerPayout payout = pendingPayout("po-4", "period-1", "seller-1");

        adapter.execute(payout);

        // The log MUST contain the anti-green-wash phrase (AC-1, erp Noop discipline).
        boolean containsAntiGreenWash = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage()
                        .contains("simulated payout (NOT a real disbursement)"));
        assertThat(containsAntiGreenWash)
                .as("log must contain 'simulated payout (NOT a real disbursement)' — no green-washing")
                .isTrue();
    }

    @Test
    void execute_result_has_null_reason_for_paid_outcome() {
        SellerPayout payout = pendingPayout("po-5", "period-1", "seller-1");

        PayoutExecutionResult result = adapter.execute(payout);

        // PAID outcome: reason must be null (only FAILED carries a reason).
        assertThat(result.reason()).isNull();
    }

    @Test
    void multiple_executions_produce_distinct_references() {
        SellerPayout p1 = pendingPayout("po-6", "period-1", "seller-1");
        SellerPayout p2 = pendingPayout("po-7", "period-1", "seller-2");

        PayoutExecutionResult r1 = adapter.execute(p1);
        PayoutExecutionResult r2 = adapter.execute(p2);

        // Each reference must be unique (UUID component).
        assertThat(r1.reference()).isNotEqualTo(r2.reference());
    }
}
