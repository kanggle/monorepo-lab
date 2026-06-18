package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxRateOverrideView;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateOverrideInvalidException;
import com.example.finance.ledger.domain.journal.repository.FxRateOverrideRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read the tenant's FX contract-rate override for one foreign-currency pair (28th increment —
 * TASK-FIN-BE-042). Base is the fixed reporting currency (KRW in v1); only the foreign leg is a
 * parameter. Returns the persisted override, or the "absent" view ({@code present=false}) when the
 * tenant has no contract rate for the pair (resolution falls through to the feed). Tenant-scoped by
 * the caller's {@code tenantId} — tenant A's override is invisible to tenant B (AC-3). An unknown
 * currency → {@code VALIDATION_ERROR} (400).
 */
@Service
@RequiredArgsConstructor
public class GetFxRateOverrideUseCase {

    private final FxRateOverrideRepository fxRateOverrideRepository;

    @Transactional(readOnly = true)
    public FxRateOverrideView get(String tenantId, String foreignCurrencyCode) {
        Currency base = LedgerReportingCurrency.BASE;
        Currency foreign = parseCurrency(foreignCurrencyCode);
        return fxRateOverrideRepository.findOverride(tenantId, base, foreign)
                .map(FxRateOverrideView::from)
                .orElseGet(() -> FxRateOverrideView.none(base.code(), foreign.code()));
    }

    /** Map an unknown/unsupported currency code to {@code VALIDATION_ERROR} (400). */
    private static Currency parseCurrency(String code) {
        try {
            return Currency.of(code);
        } catch (Currency.UnsupportedCurrencyException e) {
            throw new FxRateOverrideInvalidException(
                    "unknown currency: " + code + " — supported: KRW, USD, EUR, JPY");
        }
    }
}
