package com.example.membership.infrastructure.payment;

import com.example.membership.domain.payment.PaymentGateway;
import com.example.membership.domain.plan.PlanLevel;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub payment gateway for bootstrap. Always returns SUCCESS.
 */
@Component
public class PaymentGatewayStub implements PaymentGateway {

    @Override
    public PaymentResult charge(String accountId, PlanLevel planLevel, int priceKrw) {
        return PaymentResult.success("stub-" + UUID.randomUUID());
    }
}
