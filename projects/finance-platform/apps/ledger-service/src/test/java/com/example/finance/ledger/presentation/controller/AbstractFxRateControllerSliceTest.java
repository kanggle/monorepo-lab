package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.GetFxRateHistoryUseCase;
import com.example.finance.ledger.application.GetFxRatesUseCase;
import com.example.finance.ledger.application.RefreshFxRateQuotesUseCase;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;

/**
 * Shared base for the three {@code @WebMvcTest(FxRateController.class)} slice tests
 * ({@code FxRateControllerSliceTest}, {@code FxRateHistoryControllerSliceTest},
 * {@code FxRatesRefreshControllerSliceTest}). They mock the identical four ports and authenticate
 * as the same actor; only their {@code @WebMvcTest} annotation and test bodies live on the concrete
 * subclasses. Inherited {@link MockitoBean} fields are discovered on the superclass by Spring's
 * bean-override support, so each subclass keeps referencing them unchanged.
 */
abstract class AbstractFxRateControllerSliceTest extends AbstractLedgerControllerSliceTest {

    @MockitoBean
    GetFxRatesUseCase getFxRates;

    @MockitoBean
    GetFxRateHistoryUseCase getFxRateHistory;

    @MockitoBean
    RefreshFxRateQuotesUseCase refreshFxRateQuotes;

    @MockitoBean
    FxRateFeedSettings fxRateFeedSettings;

    @Override
    protected ActorContext actor() {
        return new ActorContext("operator-1", "finance", Set.of("finance.read"));
    }
}
