package com.example.product.domain.model;

/**
 * Minimal seller lifecycle status (ADR-MONO-030 Step 3 §3.1). v1 only registers
 * sellers in the {@code ACTIVE} state; suspension/closure is out of scope (Step 4).
 */
public enum SellerStatus {
    ACTIVE
}
