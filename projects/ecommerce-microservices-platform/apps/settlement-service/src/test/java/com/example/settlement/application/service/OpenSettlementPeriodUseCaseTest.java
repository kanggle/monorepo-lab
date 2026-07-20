package com.example.settlement.application.service;

import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.period.PeriodAlreadyOpenException;
import com.example.settlement.domain.period.PeriodStatus;
import com.example.settlement.domain.period.PeriodWindowInvalidException;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpenSettlementPeriodUseCase} unit tests: a valid window opens an OPEN
 * period; an inverted window is rejected (422 {@code PERIOD_WINDOW_INVALID}) with no
 * persistence; a duplicate window's unique-index violation becomes 409
 * {@code PERIOD_ALREADY_OPEN} (TASK-BE-535 AC-4 / AC-5).
 */
@ExtendWith(MockitoExtension.class)
class OpenSettlementPeriodUseCaseTest {

    private static final String TENANT = "tenantA";
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

    @Mock
    private SettlementPeriodRepository periodRepository;
    @InjectMocks
    private OpenSettlementPeriodUseCase useCase;

    @Test
    void open_valid_window_persists_open_period() {
        when(periodRepository.insertOpen(any())).thenAnswer(inv -> inv.getArgument(0));

        PeriodView view = useCase.open(TENANT, FROM, TO);

        assertThat(view.status()).isEqualTo(PeriodStatus.OPEN.name());
        assertThat(view.from()).isEqualTo(FROM);
        assertThat(view.to()).isEqualTo(TO);
        assertThat(view.closedAt()).isNull();
        assertThat(view.sellerCount()).isNull();
        verify(periodRepository).insertOpen(any(SettlementPeriod.class));
    }

    @Test
    void open_inverted_window_rejected_no_persistence() {
        assertThatThrownBy(() -> useCase.open(TENANT, TO, FROM))
                .isInstanceOf(PeriodWindowInvalidException.class);
        verify(periodRepository, never()).insertOpen(any());
    }

    /**
     * TASK-BE-535 AC-4 / AC-5 — the guard is the V6 partial unique index, so the
     * duplicate (sequential replay OR the loser of a concurrent race) arrives as a
     * {@link DataIntegrityViolationException} from the flushing insert. It must be
     * translated to {@link PeriodAlreadyOpenException} → 409 {@code
     * PERIOD_ALREADY_OPEN}, never allowed to escape as a 500.
     */
    @Test
    void open_duplicate_window_translates_constraint_violation_to_conflict() {
        when(periodRepository.insertOpen(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint "
                                + "\"ux_settlement_period_open_window\""));

        assertThatThrownBy(() -> useCase.open(TENANT, FROM, TO))
                .isInstanceOf(PeriodAlreadyOpenException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
