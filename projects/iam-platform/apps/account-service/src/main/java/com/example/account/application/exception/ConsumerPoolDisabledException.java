package com.example.account.application.exception;

/**
 * TASK-BE-618 — a consumer-pool maintenance run was refused as a whole because
 * {@code iam.consumer-pool.enabled} is off. Nothing is written. Maps to 409 {@code CONSUMER_POOL_DISABLED}
 * (account-maintenance-internal.md): with the flag off the site-scoped lookups no longer include pool
 * members, so a moved account would disappear from its site.
 */
public class ConsumerPoolDisabledException extends RuntimeException {

    public ConsumerPoolDisabledException() {
        super("The consumer-account pool is disabled (iam.consumer-pool.enabled=false)");
    }
}
