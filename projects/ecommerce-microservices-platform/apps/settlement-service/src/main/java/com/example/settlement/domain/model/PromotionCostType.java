package com.example.settlement.domain.model;

/** Row kind of the append-only promotion-cost ledger (TASK-BE-592). */
public enum PromotionCostType {
    /** The coupon discount the platform bore, booked when the payment is captured (positive). */
    COST,
    /** A refund's share of that discount, clawed back (negative, linked to its COST row). */
    REVERSAL
}
