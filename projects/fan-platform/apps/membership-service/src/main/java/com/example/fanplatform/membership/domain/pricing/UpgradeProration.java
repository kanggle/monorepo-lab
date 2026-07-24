package com.example.fanplatform.membership.domain.pricing;

/**
 * Pure MEMBERS_ONLY → PREMIUM upgrade proration (TASK-FAN-BE-032). Given the whole
 * remaining days of an active members-only membership, credits its unused value
 * against the premium charge:
 *
 * <pre>
 *   listPrice = premiumMonthly × planMonths
 *   credit    = min(listPrice, remainingDays × membersMonthly / DAYS_PER_MONTH)
 *   charge    = listPrice − credit          (≥ 0)
 * </pre>
 *
 * <p>Whole-day, integer arithmetic (KRW has no sub-unit) so the quote the client
 * pays and the charge the backend re-verifies are the SAME value — a mismatch
 * would wrongfully decline the PortOne payment (§ Failure Scenarios). The credit is
 * capped at the list price (never a negative charge; the surplus is forfeited, not
 * refunded — this task does not pay out credit as money).
 */
public final class UpgradeProration {

    private UpgradeProration() {
    }

    public record Quote(long listPriceMinor, long creditMinor, long chargeMinor, long remainingDays) {
    }

    /**
     * @param remainingDays      whole remaining days of the active members-only
     *                           membership (≥ 0; 0 when expired/expiring today)
     * @param planMonths         premium plan length in whole months (≥ 1)
     * @param premiumMonthlyMinor premium monthly list price
     * @param membersMonthlyMinor members-only monthly price (the credit rate)
     */
    public static Quote quote(long remainingDays, int planMonths,
                              long premiumMonthlyMinor, long membersMonthlyMinor) {
        long safeDays = Math.max(0L, remainingDays);
        long listPrice = premiumMonthlyMinor * planMonths;
        // Multiply before dividing to minimise rounding loss; floors (won).
        long rawCredit = (safeDays * membersMonthlyMinor) / MembershipPricing.DAYS_PER_MONTH;
        long credit = Math.min(rawCredit, listPrice);
        long charge = listPrice - credit;
        return new Quote(listPrice, credit, charge, safeDays);
    }
}
