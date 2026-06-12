package com.example.finance.ledger.domain.period;

import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodAlreadyClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodInvalidWindowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountingPeriodTest {

    private static final String TENANT = "finance";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");

    private static AccountingPeriod openPeriod() {
        return AccountingPeriod.open("p-1", TENANT, FROM, TO);
    }

    @Test
    @DisplayName("open creates an OPEN period over a valid window")
    void openOk() {
        AccountingPeriod p = openPeriod();
        assertThat(p.status()).isEqualTo(PeriodStatus.OPEN);
        assertThat(p.from()).isEqualTo(FROM);
        assertThat(p.to()).isEqualTo(TO);
        assertThat(p.isClosed()).isFalse();
        assertThat(p.closedAt()).isNull();
        assertThat(p.closedBy()).isNull();
        assertThat(p.entryCount()).isNull();
    }

    @Test
    @DisplayName("from >= to → AccountingPeriodInvalidWindowException (empty half-open window)")
    void invalidWindowRejected() {
        assertThatThrownBy(() -> AccountingPeriod.open("p-1", TENANT, TO, FROM))
                .isInstanceOf(AccountingPeriodInvalidWindowException.class);
        assertThatThrownBy(() -> AccountingPeriod.open("p-1", TENANT, FROM, FROM))
                .isInstanceOf(AccountingPeriodInvalidWindowException.class);
    }

    @Test
    @DisplayName("close transitions OPEN→CLOSED stamping closedAt/closedBy/entryCount")
    void closeOk() {
        AccountingPeriod p = openPeriod();
        Instant closedAt = Instant.parse("2026-02-01T01:00:00Z");

        p.close(closedAt, "user-1", 12L);

        assertThat(p.status()).isEqualTo(PeriodStatus.CLOSED);
        assertThat(p.isClosed()).isTrue();
        assertThat(p.closedAt()).isEqualTo(closedAt);
        assertThat(p.closedBy()).isEqualTo("user-1");
        assertThat(p.entryCount()).isEqualTo(12L);
    }

    @Test
    @DisplayName("a second close → AccountingPeriodAlreadyClosedException (no reopen)")
    void recloseRejected() {
        AccountingPeriod p = openPeriod();
        p.close(Instant.now(), "user-1", 0L);
        assertThatThrownBy(() -> p.close(Instant.now(), "user-2", 0L))
                .isInstanceOf(AccountingPeriodAlreadyClosedException.class);
    }

    @Test
    @DisplayName("covers — from inclusive, to exclusive (half-open boundary)")
    void coversBoundary() {
        AccountingPeriod p = openPeriod();
        assertThat(p.covers(FROM)).isTrue();                 // from is inclusive
        assertThat(p.covers(TO)).isFalse();                  // to is exclusive
        assertThat(p.covers(FROM.plusSeconds(1))).isTrue();  // inside
        assertThat(p.covers(FROM.minusSeconds(1))).isFalse(); // before
        assertThat(p.covers(TO.plusSeconds(1))).isFalse();   // after
    }

    @Test
    @DisplayName("overlaps — abutting windows do NOT overlap; nested/straddling windows do")
    void overlaps() {
        AccountingPeriod p = openPeriod(); // [Jan 1, Feb 1)
        // abutting before: [Dec 1, Jan 1) — to == from → no overlap
        assertThat(p.overlaps(Instant.parse("2025-12-01T00:00:00Z"), FROM)).isFalse();
        // abutting after: [Feb 1, Mar 1) — from == to → no overlap
        assertThat(p.overlaps(TO, Instant.parse("2026-03-01T00:00:00Z"))).isFalse();
        // straddling start
        assertThat(p.overlaps(Instant.parse("2025-12-15T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"))).isTrue();
        // nested inside
        assertThat(p.overlaps(Instant.parse("2026-01-10T00:00:00Z"),
                Instant.parse("2026-01-20T00:00:00Z"))).isTrue();
        // fully before
        assertThat(p.overlaps(Instant.parse("2025-11-01T00:00:00Z"),
                Instant.parse("2025-12-01T00:00:00Z"))).isFalse();
    }
}
