package com.example.finance.account.domain.compliance;

import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.error.DomainErrors.KycLevelInsufficientException;
import com.example.finance.account.domain.error.DomainErrors.KycRequiredException;
import com.example.finance.account.domain.error.DomainErrors.TransactionLimitExceededException;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.TransactionType;

import java.util.Map;

/**
 * Pure KYC-level policy (architecture.md § KYC/AML Compliance Gate, fintech
 * F4). Stateless — the single fund-movement application path calls
 * {@link #ensurePermitted} BEFORE any balance mutation; there is no other call
 * site, so the gate is structurally un-bypassable.
 *
 * <p>Per-level fund ceilings (minor units, currency-agnostic for v1 — a
 * conservative single threshold; v2 derives per-currency policy):
 * <ul>
 *   <li>NONE  — no fund movement permitted ({@code KYC_REQUIRED}).</li>
 *   <li>BASIC — capped at {@link #BASIC_LIMIT_MINOR}.</li>
 *   <li>FULL  — standard ceiling {@link #FULL_LIMIT_MINOR}.</li>
 * </ul>
 * RELEASE is always permitted (returning held funds reduces exposure and must
 * never be blocked by a KYC ceiling).
 */
public final class KycGate {

    public static final long BASIC_LIMIT_MINOR = 1_000_000L;     // e.g. ₩1,000,000 / $10,000.00
    public static final long FULL_LIMIT_MINOR = 100_000_000L;     // e.g. ₩100,000,000 / $1,000,000.00

    private static final Map<KycLevel, Long> LIMITS = Map.of(
            KycLevel.NONE, 0L,
            KycLevel.BASIC, BASIC_LIMIT_MINOR,
            KycLevel.FULL, FULL_LIMIT_MINOR);

    private KycGate() {
    }

    /**
     * Enforce the KYC ceiling for {@code (level, txType, amount)}.
     *
     * @throws KycRequiredException          level NONE attempting fund movement
     * @throws KycLevelInsufficientException level below the minimum for the op
     * @throws TransactionLimitExceededException amount over the level ceiling
     */
    public static void ensurePermitted(KycLevel level,
                                       TransactionType txType,
                                       Money amount) {
        // Returning funds (release) never blocked by a KYC ceiling.
        if (txType == TransactionType.RELEASE) {
            return;
        }
        if (level == KycLevel.NONE) {
            throw new KycRequiredException(
                    "KYC required for " + txType + " — account KYC level is NONE");
        }
        // TRANSFER requires at least BASIC (already guaranteed by != NONE here,
        // but kept explicit so future per-type minimums slot in cleanly).
        if (txType == TransactionType.TRANSFER && !level.isAtLeast(KycLevel.BASIC)) {
            throw new KycLevelInsufficientException(
                    "TRANSFER requires KYC level BASIC or higher; current=" + level);
        }
        long ceiling = LIMITS.getOrDefault(level, 0L);
        if (amount.minorUnits() > ceiling) {
            throw new TransactionLimitExceededException(
                    "amount " + amount.minorUnits() + " exceeds KYC " + level
                            + " ceiling " + ceiling);
        }
    }
}
