package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * One debit-or-credit line of a {@link JournalEntry} (architecture.md § Layer
 * Structure). Immutable once posted — there is no business setter and the owning
 * entry never updates a line (F3). Money is integer minor units only (F5).
 *
 * <p>The {@code entry_id} + {@code posted_at} columns are denormalized onto the
 * line so the per-account lines view and the trial-balance totals are simple
 * tenant-scoped line queries (no entry join). They are stamped by the owning
 * entry at post time ({@link JournalEntry#post}). JPA annotations are the allowed
 * domain↔framework exception; the line's semantics are pure.
 */
@Entity
@Table(name = "journal_line")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "entry_id", length = 36, nullable = false)
    private String entryId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", length = 10, nullable = false)
    private EntryDirection direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    private JournalLine(String tenantId, String ledgerAccountCode,
                        EntryDirection direction, Money money) {
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.direction = direction;
        this.amountMinor = money.minorUnits();
        this.currency = money.currency();
    }

    public static JournalLine of(String tenantId, String ledgerAccountCode,
                                 EntryDirection direction, Money money) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(money, "money");
        if (!money.isPositive()) {
            throw new IllegalArgumentException(
                    "journal line amount must be positive: " + money);
        }
        return new JournalLine(tenantId, ledgerAccountCode, direction, money);
    }

    public static JournalLine debit(String tenantId, String code, Money money) {
        return of(tenantId, code, EntryDirection.DEBIT, money);
    }

    public static JournalLine credit(String tenantId, String code, Money money) {
        return of(tenantId, code, EntryDirection.CREDIT, money);
    }

    /** Stamp the owning entry's id + posting instant (called by the entry factory). */
    void attachTo(String entryId, Instant postedAt) {
        this.entryId = entryId;
        this.postedAt = postedAt;
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }

    public boolean isDebit() {
        return direction == EntryDirection.DEBIT;
    }

    public boolean isCredit() {
        return direction == EntryDirection.CREDIT;
    }

    /** A line on the opposite side with the same account + money (reversal, F3). */
    public JournalLine reversed() {
        return new JournalLine(tenantId, ledgerAccountCode, direction.opposite(), money());
    }
}
