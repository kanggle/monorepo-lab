package com.example.settlement.domain.period;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SettlementPeriod} aggregate unit tests (AC-2) — mirrors finance
 * {@code AccountingPeriodTest}: open/close transitions, window validation, re-close
 * guard, and the half-open {@code covers}/{@code overlaps} predicates.
 */
class SettlementPeriodTest {

    private static final String TENANT = "ecommerce";
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

    private static SettlementPeriod openPeriod() {
        return SettlementPeriod.open("p-1", TENANT, FROM, TO);
    }

    @Test
    @DisplayName("open creates an OPEN period over a valid window")
    void openOk() {
        SettlementPeriod p = openPeriod();
        assertThat(p.status()).isEqualTo(PeriodStatus.OPEN);
        assertThat(p.from()).isEqualTo(FROM);
        assertThat(p.to()).isEqualTo(TO);
        assertThat(p.isClosed()).isFalse();
        assertThat(p.closedAt()).isNull();
        assertThat(p.closedBy()).isNull();
        assertThat(p.sellerCount()).isNull();
    }

    @Test
    @DisplayName("from >= to → PeriodWindowInvalidException (empty half-open window)")
    void invalidWindowRejected() {
        assertThatThrownBy(() -> SettlementPeriod.open("p-1", TENANT, TO, FROM))
                .isInstanceOf(PeriodWindowInvalidException.class);
        assertThatThrownBy(() -> SettlementPeriod.open("p-1", TENANT, FROM, FROM))
                .isInstanceOf(PeriodWindowInvalidException.class);
    }

    @Test
    @DisplayName("close transitions OPEN→CLOSED stamping closedAt/closedBy/sellerCount")
    void closeOk() {
        SettlementPeriod p = openPeriod();
        Instant closedAt = Instant.parse("2026-07-01T09:00:00Z");

        p.close(closedAt, "operator-1", 2);

        assertThat(p.status()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(p.isClosed()).isTrue();
        assertThat(p.closedAt()).isEqualTo(closedAt);
        assertThat(p.closedBy()).isEqualTo("operator-1");
        assertThat(p.sellerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a second close → PeriodAlreadyClosedException (no reopen)")
    void recloseRejected() {
        SettlementPeriod p = openPeriod();
        p.close(Instant.now(), "operator-1", 0);
        assertThatThrownBy(() -> p.close(Instant.now(), "operator-2", 0))
                .isInstanceOf(PeriodAlreadyClosedException.class);
    }

    @Test
    @DisplayName("covers — from inclusive, to exclusive (half-open boundary)")
    void coversBoundary() {
        SettlementPeriod p = openPeriod();
        assertThat(p.covers(FROM)).isTrue();                  // from is inclusive
        assertThat(p.covers(TO)).isFalse();                   // to is exclusive
        assertThat(p.covers(FROM.plusSeconds(1))).isTrue();   // inside
        assertThat(p.covers(FROM.minusSeconds(1))).isFalse(); // before
        assertThat(p.covers(TO.plusSeconds(1))).isFalse();    // after
    }

    @Test
    @DisplayName("overlaps — abutting windows do NOT overlap; nested/straddling windows do")
    void overlaps() {
        SettlementPeriod p = openPeriod(); // [Jun 1, Jul 1)
        // abutting before: [May 1, Jun 1) — to == from → no overlap
        assertThat(p.overlaps(Instant.parse("2026-05-01T00:00:00Z"), FROM)).isFalse();
        // abutting after: [Jul 1, Aug 1) — from == to → no overlap
        assertThat(p.overlaps(TO, Instant.parse("2026-08-01T00:00:00Z"))).isFalse();
        // straddling start
        assertThat(p.overlaps(Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"))).isTrue();
        // nested inside
        assertThat(p.overlaps(Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-20T00:00:00Z"))).isTrue();
        // fully before
        assertThat(p.overlaps(Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"))).isFalse();
    }
}
