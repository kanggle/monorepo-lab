package com.example.finance.account.domain.compliance;

/**
 * AML/sanction screening result VO (architecture.md § KYC/AML Compliance
 * Gate). Pure value — produced by the {@code CompliancePort} adapter and
 * consumed by the single fund-movement application path.
 *
 * <ul>
 *   <li>{@code CLEAR} — screening passed, fund movement may proceed.</li>
 *   <li>{@code SANCTION_HIT} — sanction/watchlist match → txn FAILED +
 *       operator queue, no auto-clear (F4).</li>
 *   <li>{@code SCREENING_UNRESOLVED} → {@code AML_SCREENING_REQUIRED}.</li>
 * </ul>
 *
 * <p>{@code screeningRef} is a non-PII correlation id; matched-list detail is
 * NOT carried here (it lives in the operator queue only — F7).
 */
public record ScreeningDecision(Outcome outcome, String screeningRef) {

    public enum Outcome {
        CLEAR,
        SANCTION_HIT,
        SCREENING_UNRESOLVED
    }

    public static ScreeningDecision clear(String screeningRef) {
        return new ScreeningDecision(Outcome.CLEAR, screeningRef);
    }

    public static ScreeningDecision sanctionHit(String screeningRef) {
        return new ScreeningDecision(Outcome.SANCTION_HIT, screeningRef);
    }

    public static ScreeningDecision unresolved(String screeningRef) {
        return new ScreeningDecision(Outcome.SCREENING_UNRESOLVED, screeningRef);
    }

    public boolean isClear() {
        return outcome == Outcome.CLEAR;
    }

    public boolean isSanctionHit() {
        return outcome == Outcome.SANCTION_HIT;
    }
}
