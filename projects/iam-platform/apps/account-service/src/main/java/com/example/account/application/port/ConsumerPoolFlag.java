package com.example.account.application.port;

/**
 * TASK-BE-614 — the {@code iam.consumer-pool.enabled} feature flag (default {@code false}).
 *
 * <p>While it is off, signup, the site-scoped account lookups and {@code account.created} behave
 * exactly as before ADR-MONO-078: nothing is born into the pool and no lookup consults
 * {@code consumer_site_memberships}. It exists because pool <i>login</i> lands separately
 * ({@code TASK-BE-615}); turning it on before that ships would let people sign up into an account
 * they cannot log in to. {@code TASK-BE-615} turns it on together with login.
 *
 * <p>Fails toward the old behaviour: an absent property is {@code false}.
 */
public interface ConsumerPoolFlag {

    boolean isEnabled();
}
