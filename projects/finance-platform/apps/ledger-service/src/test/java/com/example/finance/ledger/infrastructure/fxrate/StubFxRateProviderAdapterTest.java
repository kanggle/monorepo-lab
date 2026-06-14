package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort.RateQuote;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link StubFxRateProviderAdapter} (AC-3 / AC-5 stub leg). A configured foreign code
 * returns its fixed rate with {@code asOf=clock.now()} and {@code source="stub"}; an unconfigured
 * code returns empty. No Spring context — properties + a fixed clock are constructed directly.
 */
class StubFxRateProviderAdapterTest {

    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    private StubFxRateProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        FxRateFeedProperties props = new FxRateFeedProperties();
        props.getStub().getRates().put("USD", new BigDecimal("1300.0"));
        props.getStub().getRates().put("EUR", new BigDecimal("1450.0"));
        ClockPort clock = () -> NOW;
        adapter = new StubFxRateProviderAdapter(props, clock);
    }

    @Test
    void returnsConfiguredRateForKnownPair() {
        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.USD);

        assertThat(quote).isPresent();
        assertThat(quote.get().rate()).isEqualByComparingTo("1300.0");
        assertThat(quote.get().asOf()).isEqualTo(NOW);
        assertThat(quote.get().source()).isEqualTo("stub");
    }

    @Test
    void returnsEmptyForUnconfiguredPair() {
        // JPY is not in the stub rate map.
        Optional<RateQuote> quote = adapter.latestQuote(Currency.KRW, Currency.JPY);

        assertThat(quote).isEmpty();
    }
}
