package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Deterministic stub FX rate adapter (23rd increment — TASK-FIN-BE-031, ADR-002 D5). Returns a
 * fixed rate from {@code financeplatform.ledger.fxrate.stub.rates} keyed by the FOREIGN currency
 * code, with {@code asOf=clock.now()} and {@code source="stub"}. An unconfigured pair → empty.
 * Wired when {@code financeplatform.ledger.fxrate.mode=stub}. No external dependency — demos / ITs
 * exercise the full feed (poller → cache) without a third-party API.
 */
@Component
@ConditionalOnProperty(name = "financeplatform.ledger.fxrate.mode", havingValue = "stub")
@RequiredArgsConstructor
public class StubFxRateProviderAdapter implements FxRateProviderPort {

    private final FxRateFeedProperties properties;
    private final ClockPort clock;

    @Override
    public Optional<RateQuote> latestQuote(Currency base, Currency foreign) {
        BigDecimal rate = properties.getStub().getRates().get(foreign.code());
        if (rate == null) {
            return Optional.empty();
        }
        return Optional.of(new RateQuote(rate, clock.now(), "stub"));
    }
}
