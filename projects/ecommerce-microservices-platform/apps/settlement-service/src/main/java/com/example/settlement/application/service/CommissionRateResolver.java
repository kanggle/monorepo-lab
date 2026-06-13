package com.example.settlement.application.service;

import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.repository.CommissionRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves the effective commission rate for a {@code (tenant_id, seller_id)} pair:
 * the per-seller override if present, else the platform default
 * {@code settlement.commission.default-rate-bps} (AC-4). The default may be {@code 0}
 * (net-zero degrade, D8 / AC-9).
 *
 * <p>An application service: it joins the rate repository with the configured
 * default. The split arithmetic itself lives in the domain {@code CommissionPolicy}.
 */
@Service
@RequiredArgsConstructor
public class CommissionRateResolver {

    private final CommissionRateRepository rateRepository;

    @Value("${settlement.commission.default-rate-bps:0}")
    private int defaultRateBps;

    /** The effective rate (override-or-default) for {@code (tenantId, sellerId)}. */
    public CommissionRate resolve(String tenantId, String sellerId) {
        return rateRepository.findRateBps(tenantId, sellerId)
                .map(CommissionRate::sellerOverride)
                .orElseGet(() -> CommissionRate.platformDefault(defaultRateBps));
    }

    int defaultRateBps() {
        return defaultRateBps;
    }
}
