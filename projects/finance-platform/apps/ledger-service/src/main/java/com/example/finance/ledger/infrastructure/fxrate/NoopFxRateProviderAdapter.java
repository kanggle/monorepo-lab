package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.FxRateProviderPort;
import com.example.finance.ledger.domain.money.Currency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default FX rate adapter (23rd increment — TASK-FIN-BE-031, ADR-002 D1) — always
 * {@link Optional#empty()}, making ZERO external calls. Wired when
 * {@code financeplatform.ledger.fxrate.mode} is absent or {@code noop} ({@code matchIfMissing=true}),
 * so an unconfigured service stays inert (net-zero). Exactly one of the three adapters is active per
 * {@code mode} (disjoint {@code havingValue}; only this one carries {@code matchIfMissing}).
 */
@Component
@ConditionalOnProperty(name = "financeplatform.ledger.fxrate.mode", havingValue = "noop",
        matchIfMissing = true)
public class NoopFxRateProviderAdapter implements FxRateProviderPort {

    @Override
    public Optional<RateQuote> latestQuote(Currency base, Currency foreign) {
        return Optional.empty();
    }
}
