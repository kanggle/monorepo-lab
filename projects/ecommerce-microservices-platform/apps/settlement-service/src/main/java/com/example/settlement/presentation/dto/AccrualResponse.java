package com.example.settlement.presentation.dto;

import com.example.settlement.domain.model.CommissionAccrual;

/** One accrual/reversal row in the {@code GET /accruals} response (settlement-api.md). */
public record AccrualResponse(
        String accrualId,
        String orderId,
        String paymentId,
        String sellerId,
        String type,
        long grossMinor,
        int rateBps,
        long commissionMinor,
        long sellerNetMinor,
        String occurredAt) {

    public static AccrualResponse from(CommissionAccrual a) {
        return new AccrualResponse(
                a.accrualId(), a.orderId(), a.paymentId(), a.sellerId(),
                a.type().name(), a.grossMinor(), a.rateBps(),
                a.commissionMinor(), a.sellerNetMinor(),
                a.occurredAt() == null ? null : a.occurredAt().toString());
    }
}
