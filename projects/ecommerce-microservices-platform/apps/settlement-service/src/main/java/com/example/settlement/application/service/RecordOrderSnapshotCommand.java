package com.example.settlement.application.service;

import com.example.settlement.domain.model.OrderSnapshotLine;

import java.util.List;

/**
 * Command to cache an {@code OrderPlaced} line snapshot. {@code tenantId} is the
 * event-envelope tenant (settlement's authoritative tenant source, AC-7).
 */
public record RecordOrderSnapshotCommand(String orderId, String tenantId, List<OrderSnapshotLine> lines) {
}
