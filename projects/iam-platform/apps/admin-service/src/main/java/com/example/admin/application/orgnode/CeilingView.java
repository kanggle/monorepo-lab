package com.example.admin.application.orgnode;

import java.util.List;
import java.util.Objects;

/**
 * TASK-BE-492 (ADR-MONO-047 D2) — the admin-side wire/view shape of an org-node's
 * <b>entitlement ceiling</b>: a deny-only guardrail that <em>narrows</em> a tenant's
 * entitled domains and can never grant one.
 *
 * <p><b>{@code UNBOUNDED} and {@code BOUNDED([])} are opposites</b>, and the whole reason
 * this carries an explicit {@code mode} rather than a nullable domain list:
 * <ul>
 *   <li>{@code UNBOUNDED} = <i>no ceiling</i> = the intersection identity. It is NOT
 *       "every currently-known domain" — a domain added tomorrow must still be permitted
 *       by a legacy unbounded node.</li>
 *   <li>{@code BOUNDED([])} = permits nothing (fail-closed).</li>
 * </ul>
 *
 * <p>The ceiling math itself lives in account-service ({@code EntitlementCeiling},
 * TASK-BE-491) — this type only carries the value across the internal boundary and
 * answers the two questions the admin-side grant cap asks. admin-service never
 * intersects chains; it reads {@code effectiveCeiling(N)} from the authority.
 *
 * @param mode    {@code "UNBOUNDED"} or {@code "BOUNDED"}
 * @param domains the permitted domain keys when {@code BOUNDED}; empty when {@code UNBOUNDED}
 */
public record CeilingView(String mode, List<String> domains) {

    public static final String MODE_UNBOUNDED = "UNBOUNDED";
    public static final String MODE_BOUNDED = "BOUNDED";

    public CeilingView {
        Objects.requireNonNull(mode, "mode");
        if (!MODE_UNBOUNDED.equals(mode) && !MODE_BOUNDED.equals(mode)) {
            throw new IllegalArgumentException("ceiling.mode must be UNBOUNDED or BOUNDED: " + mode);
        }
        domains = domains == null ? List.of() : List.copyOf(domains);
        if (MODE_UNBOUNDED.equals(mode) && !domains.isEmpty()) {
            throw new IllegalArgumentException("An UNBOUNDED ceiling carries no domains");
        }
    }

    public static CeilingView unbounded() {
        return new CeilingView(MODE_UNBOUNDED, List.of());
    }

    public static CeilingView bounded(List<String> domains) {
        return new CeilingView(MODE_BOUNDED, domains);
    }

    /**
     * The fail-closed default. A ceiling that could not be resolved (account-service down /
     * circuit open / timeout) resolves to this — <b>never</b> to {@link #unbounded()}.
     */
    public static CeilingView failClosed() {
        return new CeilingView(MODE_BOUNDED, List.of());
    }

    public boolean isUnbounded() {
        return MODE_UNBOUNDED.equals(mode);
    }

    /** {@code BOUNDED([])} — permits nothing. The opposite of {@link #isUnbounded()}. */
    public boolean permitsNothing() {
        return MODE_BOUNDED.equals(mode) && domains.isEmpty();
    }

    /** {@code true} iff this ceiling permits {@code domainKey} (UNBOUNDED permits everything). */
    public boolean permits(String domainKey) {
        return isUnbounded() || domains.contains(domainKey);
    }
}
