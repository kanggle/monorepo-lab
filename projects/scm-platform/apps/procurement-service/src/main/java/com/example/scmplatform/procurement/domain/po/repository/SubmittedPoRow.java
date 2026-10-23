package com.example.scmplatform.procurement.domain.po.repository;

import com.example.scmplatform.procurement.domain.po.status.PoStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One PO submitted inside a metrics window (TASK-MONO-810): its submit instant, its
 * <em>current</em> status and its total in its own currency. The status is carried so the
 * application service — not the query — applies the {@code CANCELED} exclusion
 * ({@code procurement-api.md} § GET /po/metrics, {@code poCount}).
 */
public record SubmittedPoRow(Instant submittedAt, PoStatus status, BigDecimal totalAmount, String currency) {
}
