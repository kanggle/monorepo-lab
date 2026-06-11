package com.example.scmplatform.demandplanning.domain.error;

/**
 * Raised when a reorder policy is not found. Maps to HTTP 404 POLICY_NOT_FOUND.
 */
public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String skuCode) {
        super("Reorder policy not found for skuCode: " + skuCode);
    }
}
