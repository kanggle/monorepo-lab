package com.example.settlement.domain.model;

/**
 * One line of a cached {@code OrderPlaced} snapshot: the seller this line is
 * attributed to (inner marketplace axis, Step 3) and the line's gross amount in
 * minor units ({@code gross_minor = unitPrice × quantity}). The snapshot is the
 * only source of a line's {@code seller_id} for settlement.
 */
public record OrderSnapshotLine(String sellerId, long grossMinor) {
}
