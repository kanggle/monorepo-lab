package com.example.settlement.application.service;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.settlement.application.port.SettlementMetricsPort;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.payout.SellerPayout;
import com.example.settlement.domain.period.PeriodAlreadyClosedException;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.PeriodStatus;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.SellerAccrualFold;
import com.example.settlement.domain.repository.SellerPayoutRepository;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CloseSettlementPeriodUseCase} unit tests (AC-3/AC-4/AC-5/AC-6): the
 * per-seller fold, the net-zero skip (decision 7), the outbox-append (mocked
 * publisher), {@code sellerCount == payouts.length}, and the 404 / 409 guards.
 */
@ExtendWith(MockitoExtension.class)
class CloseSettlementPeriodUseCaseTest {

    private static final String TENANT = "tenantA";
    private static final String PERIOD_ID = "p-1";
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-07-01T09:00:00Z");

    @Mock
    private SettlementPeriodRepository periodRepository;
    @Mock
    private SellerPayoutRepository payoutRepository;
    @Mock
    private CommissionAccrualRepository accrualRepository;
    @Mock
    private SettlementEventPublisher eventPublisher;
    @Mock
    private SettlementMetricsPort metrics;

    private final Clock clock = Clock.fixed(CLOSED_AT, ZoneOffset.UTC);

    private CloseSettlementPeriodUseCase useCaseWithClock() {
        return new CloseSettlementPeriodUseCase(periodRepository, payoutRepository,
                accrualRepository, eventPublisher, metrics, clock);
    }

    private static SettlementPeriod openPeriod() {
        return SettlementPeriod.open(PERIOD_ID, TENANT, FROM, TO);
    }

    @Test
    void close_folds_accruals_into_pending_payouts_and_appends_outbox() {
        when(periodRepository.findById(PERIOD_ID, TENANT)).thenReturn(Optional.of(openPeriod()));
        when(accrualRepository.foldByPeriod(TENANT, FROM, TO)).thenReturn(List.of(
                new SellerAccrualFold("seller-1", 27_000L, 3_000L, 1),
                new SellerAccrualFold("seller-2", 18_000L, 2_000L, 2)));
        when(payoutRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PeriodView view = useCaseWithClock().close(PERIOD_ID, TENANT, "operator-1");

        // Two PENDING payouts created (decision 7: both positive).
        ArgumentCaptor<List<SellerPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
        verify(payoutRepository).saveAll(payoutsCaptor.capture());
        List<SellerPayout> saved = payoutsCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(p -> {
            assertThat(p.periodId()).isEqualTo(PERIOD_ID);
            assertThat(p.tenantId()).isEqualTo(TENANT);
            assertThat(p.status().name()).isEqualTo("PENDING");
        });

        // Period CLOSED with sellerCount == #payouts.
        assertThat(view.status()).isEqualTo(PeriodStatus.CLOSED.name());
        assertThat(view.sellerCount()).isEqualTo(2);
        assertThat(view.closedAt()).isEqualTo(CLOSED_AT);
        assertThat(view.payouts()).hasSize(2);

        // Event appended to the outbox: payload matches the fold; seller_count == payouts.length.
        ArgumentCaptor<SettlementPeriodClosedEvent> eventCaptor =
                ArgumentCaptor.forClass(SettlementPeriodClosedEvent.class);
        verify(eventPublisher).publishPeriodClosed(eventCaptor.capture());
        SettlementPeriodClosedEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("settlement.period.closed.v1");
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.payload().sellerCount()).isEqualTo(2);
        assertThat(event.payload().payouts()).hasSize(2);
        assertThat(event.payload().payouts().get(0).sellerId()).isEqualTo("seller-1");
        assertThat(event.payload().payouts().get(0).payableNetMinor()).isEqualTo(27_000L);

        verify(metrics).recordPeriodClosed();
    }

    @Test
    void close_skips_net_zero_sellers_decision7() {
        when(periodRepository.findById(PERIOD_ID, TENANT)).thenReturn(Optional.of(openPeriod()));
        when(accrualRepository.foldByPeriod(TENANT, FROM, TO)).thenReturn(List.of(
                new SellerAccrualFold("seller-paid", 27_000L, 3_000L, 1),
                new SellerAccrualFold("seller-zero", 0L, 0L, 2),       // fully reversed
                new SellerAccrualFold("seller-neg", -500L, -50L, 3))); // negative payable
        when(payoutRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PeriodView view = useCaseWithClock().close(PERIOD_ID, TENANT, "operator-1");

        ArgumentCaptor<List<SellerPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
        verify(payoutRepository).saveAll(payoutsCaptor.capture());
        List<SellerPayout> saved = payoutsCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).sellerId()).isEqualTo("seller-paid");

        // sellerCount counts only positive-payable sellers.
        assertThat(view.sellerCount()).isEqualTo(1);

        ArgumentCaptor<SettlementPeriodClosedEvent> eventCaptor =
                ArgumentCaptor.forClass(SettlementPeriodClosedEvent.class);
        verify(eventPublisher).publishPeriodClosed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().payouts()).hasSize(1);
        assertThat(eventCaptor.getValue().payload().sellerCount()).isEqualTo(1);
    }

    @Test
    void close_empty_window_creates_no_payouts_but_closes() {
        when(periodRepository.findById(PERIOD_ID, TENANT)).thenReturn(Optional.of(openPeriod()));
        when(accrualRepository.foldByPeriod(TENANT, FROM, TO)).thenReturn(List.of());
        when(payoutRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PeriodView view = useCaseWithClock().close(PERIOD_ID, TENANT, "operator-1");

        assertThat(view.status()).isEqualTo(PeriodStatus.CLOSED.name());
        assertThat(view.sellerCount()).isZero();
        assertThat(view.payouts()).isEmpty();
        verify(eventPublisher).publishPeriodClosed(any());
        verify(metrics).recordPeriodClosed();
    }

    @Test
    void close_absent_period_throws_not_found_no_publish() {
        when(periodRepository.findById(PERIOD_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCaseWithClock().close(PERIOD_ID, TENANT, "operator-1"))
                .isInstanceOf(PeriodNotFoundException.class);

        verify(payoutRepository, never()).saveAll(anyList());
        verify(eventPublisher, never()).publishPeriodClosed(any());
        verify(metrics, never()).recordPeriodClosed();
    }

    @Test
    void close_already_closed_throws_409_no_publish() {
        SettlementPeriod closed = openPeriod();
        closed.close(Instant.now(), "operator-0", 0);
        when(periodRepository.findById(PERIOD_ID, TENANT)).thenReturn(Optional.of(closed));
        when(accrualRepository.foldByPeriod(TENANT, FROM, TO)).thenReturn(List.of());

        assertThatThrownBy(() -> useCaseWithClock().close(PERIOD_ID, TENANT, "operator-1"))
                .isInstanceOf(PeriodAlreadyClosedException.class);

        verify(eventPublisher, never()).publishPeriodClosed(any());
        verify(metrics, never()).recordPeriodClosed();
    }
}
