package com.example.settlement.presentation.dto;

import com.example.settlement.domain.model.CommissionRate;

/** {@code GET/PUT /commission-rates/{sellerId}} response (settlement-api.md). */
public record CommissionRateResponse(String sellerId, int rateBps, String source) {

    public static CommissionRateResponse from(String sellerId, CommissionRate rate) {
        return new CommissionRateResponse(sellerId, rate.rateBps(), rate.source().name());
    }
}
