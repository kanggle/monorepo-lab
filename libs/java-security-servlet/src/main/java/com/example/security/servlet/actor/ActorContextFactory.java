package com.example.security.servlet.actor;

import java.util.Set;

/**
 * Builds a service's own actor value object from the three claims
 * {@link ActorClaims} lifts (ADR-MONO-058 § D1).
 *
 * <p>This is the <strong>only</strong> seam between the shared mechanism and a project's authorization
 * policy. The library never learns the actor type, never learns a role name, and never gains a
 * {@code projects/} import; the service supplies its own record's constructor, typically as a method
 * reference:
 *
 * <pre>{@code
 * new ActorContextJwtAuthenticationConverter<>(ActorContext::new)
 * }</pre>
 *
 * <p>Records are implicitly {@code final}, so a shared record could not have been subclassed to carry a
 * service's own role predicates. Handing the service a factory instead keeps those predicates — and the
 * role literals they test — inside the service that owns them
 * (`platform/shared-library-policy.md § Ownership Rule`).
 *
 * @param <A> the service's own actor type
 */
@FunctionalInterface
public interface ActorContextFactory<A> {

    /**
     * @param accountId the JWT {@code sub}, never null or blank
     * @param tenantId  the JWT {@code tenant_id}, never null or blank
     * @param roles     the normalised role set, never null, possibly empty, {@code contains(null)}-safe
     */
    A create(String accountId, String tenantId, Set<String> roles);
}
