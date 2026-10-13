package com.example.admin.application.exception;

/**
 * 422 — a set (created, edited or granted) covers a domain outside the tenant's entitled domains (active
 * subscription ∩ org-node ceiling). When entitlement cannot be determined the write is refused as 503 instead
 * ({@link DownstreamFailureException}), never allowed.
 */
public class DomainNotSubscribedException extends DomainPermissionSetException {
    public DomainNotSubscribedException(String message) {
        super("DOMAIN_NOT_SUBSCRIBED", 422, message);
    }
}
