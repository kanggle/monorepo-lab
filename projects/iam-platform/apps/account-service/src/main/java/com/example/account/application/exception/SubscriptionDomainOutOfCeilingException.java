package com.example.account.application.exception;

/**
 * TASK-BE-491 (ADR-MONO-047 § D2 + ADR-MONO-023 plane separation): an attempt to bring a
 * {@code tenant_domain_subscription} to ACTIVE for a domain that lies outside the tenant's
 * effective org-node ceiling. Surfaces as 422 {@code SUBSCRIPTION_DOMAIN_OUT_OF_CEILING}.
 *
 * <p>The ceiling bounds <b>entitlement</b> only — it never mints an IAM role. Deactivation
 * is always allowed, because narrowing is always safe.
 */
public class SubscriptionDomainOutOfCeilingException extends RuntimeException {

    public SubscriptionDomainOutOfCeilingException(String tenantId, String domainKey) {
        super("tenant " + tenantId + " may not activate domain '" + domainKey
                + "': outside its effective org-node entitlement ceiling");
    }
}
