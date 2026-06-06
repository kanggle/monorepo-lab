package com.example.membership.domain.payment;

import com.example.membership.domain.plan.PlanLevel;

/**
 * Port: payment gateway for subscription activation. Bootstrap stub always returns SUCCESS.
 */
public interface PaymentGateway {

    PaymentResult charge(String accountId, PlanLevel planLevel, int priceKrw);

    record PaymentResult(boolean success, String transactionId, String failureReason) {
        public static PaymentResult success(String transactionId) {
            return new PaymentResult(true, transactionId, null);
        }

        public static PaymentResult failure(String reason) {
            return new PaymentResult(false, null, reason);
        }
    }
}
