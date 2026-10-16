package com.example.web.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a role-list header — the comma-joined role names an edge gateway forwards to the services behind it
 * (for example a {@code roles} claim array joined on {@code ","}).
 *
 * <h2>Why this exists</h2>
 * <p>Services that trust a gateway-injected role header each need the same small parse: split on the comma, trim,
 * compare. Written inline in every service, the copies drift — one compares case-sensitively while its siblings
 * ignore case, one does a substring test, one treats a missing header differently — and a role decision then depends
 * on which copy a request happens to reach. This class is the one place that comparison rule is defined.
 *
 * <h2>The comparison rule (all of it)</h2>
 * <ul>
 *   <li>The header is split on {@code ','}; each element is trimmed; empty elements are ignored.</li>
 *   <li>Role names compare <b>case-insensitively</b> ({@link Locale#ROOT} upper case — no locale-dependent
 *       folding).</li>
 *   <li>Membership is <b>whole-element equality</b>, never a substring: {@code "ROLE_AX"} does not hold
 *       {@code "ROLE_A"}.</li>
 *   <li>A {@code null}, empty or blank header holds <b>no role</b> — every check against it fails closed.</li>
 * </ul>
 *
 * <p>Which role a given operation needs is not decided here — the caller owns that. Framework-free (pure JDK), so it
 * is safe on both servlet and reactive classpaths.
 */
public final class RoleListHeader {

    private RoleListHeader() {
    }

    /**
     * @param headerValue the raw header value (may be {@code null})
     * @return the distinct role names in the header, upper-cased with {@link Locale#ROOT}, in header order;
     *         immutable, never {@code null} (empty for a {@code null}/blank header)
     */
    public static Set<String> parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String element : headerValue.split(",")) {
            String role = element.trim();
            if (!role.isEmpty()) {
                out.add(normalise(role));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * @param headerValue the raw header value (may be {@code null})
     * @param roles       the roles any one of which suffices (compared under the same rule); {@code null} or empty
     *                    → {@code false}
     * @return {@code true} iff the header holds at least one of {@code roles}
     */
    public static boolean holdsAny(String headerValue, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        Set<String> held = parse(headerValue);
        if (held.isEmpty()) {
            return false;
        }
        for (String role : roles) {
            if (role != null && !role.isBlank() && held.contains(normalise(role.trim()))) {
                return true;
            }
        }
        return false;
    }

    /** Varargs form of {@link #holdsAny(String, Collection)}. */
    public static boolean holdsAny(String headerValue, String... roles) {
        return roles != null && holdsAny(headerValue, java.util.Arrays.asList(roles));
    }

    private static String normalise(String role) {
        return role.toUpperCase(Locale.ROOT);
    }
}
