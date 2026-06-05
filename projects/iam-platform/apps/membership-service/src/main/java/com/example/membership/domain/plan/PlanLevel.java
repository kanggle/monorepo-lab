package com.example.membership.domain.plan;

/**
 * Membership plan level. Ordering: FREE < FAN_CLUB.
 * Use {@link #getRank()} for explicit numeric comparison.
 */
public enum PlanLevel {
    FREE(0),
    FAN_CLUB(1);

    private final int rank;

    PlanLevel(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /**
     * @return true if this plan level is at least as high as the required level.
     */
    public boolean meetsOrExceeds(PlanLevel required) {
        return this.rank >= required.rank;
    }

    public static PlanLevel parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("planLevel is required");
        }
        try {
            return PlanLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown planLevel: " + value);
        }
    }
}
