package com.example.admin.application;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * TASK-MONO-772 S6 (auth-to-admin.md § GET /internal/operators/facet — {@code axes}) — the two axes an
 * operator facet is matched on. The caller picks which ones to ask; the endpoint's predicate per axis is
 * unchanged.
 *
 * <ul>
 *   <li>{@link #OIDC_SUBJECT} — {@code admin_operators.oidc_subject = accountId} (ADR-MONO-044 D5 self-onboarded
 *       operator).</li>
 *   <li>{@link #IDENTITY} — {@code admin_operators.identity_id = identityId} (ADR-MONO-034 U3 operator identity
 *       link).</li>
 * </ul>
 *
 * <p>Since 772 S6 the consumer-pool mover asks {@code IDENTITY} only (ADR-MONO-080 D6 fourth line · implementer
 * decision D-6): a self-onboarded operator is moved under the same id instead of being skipped.
 */
public enum OperatorFacetAxis {
    OIDC_SUBJECT,
    IDENTITY;

    /** Both axes — the meaning of an omitted {@code axes} parameter (the pre-772 behaviour, byte-unchanged). */
    public static Set<OperatorFacetAxis> all() {
        return Collections.unmodifiableSet(EnumSet.allOf(OperatorFacetAxis.class));
    }

    /**
     * Parses the {@code axes} query value — a comma list of {@code OIDC_SUBJECT} / {@code IDENTITY}.
     *
     * <ul>
     *   <li>{@code null} (parameter omitted) → {@link #all()}.</li>
     *   <li>Any unknown, blank or empty element — including a blank parameter — → {@link IllegalArgumentException}
     *       ({@code 400 VALIDATION_ERROR}). 🔴 A blank list is NOT read as «no axis»: asking nothing answers
     *       {@code false}, and the mover would then move operator-faceted accounts. Refusing is the closed
     *       side.</li>
     * </ul>
     * The message never echoes the submitted value.
     */
    public static Set<OperatorFacetAxis> parse(String axes) {
        if (axes == null) {
            return all();
        }
        EnumSet<OperatorFacetAxis> parsed = EnumSet.noneOf(OperatorFacetAxis.class);
        for (String element : axes.split(",", -1)) {
            String name = element.trim();
            OperatorFacetAxis axis = byName(name);
            if (axis == null) {
                throw new IllegalArgumentException(
                        "axes must be a comma list of OIDC_SUBJECT, IDENTITY");
            }
            parsed.add(axis);
        }
        return Collections.unmodifiableSet(parsed);
    }

    private static OperatorFacetAxis byName(String name) {
        for (OperatorFacetAxis axis : values()) {
            if (axis.name().equals(name)) {
                return axis;
            }
        }
        return null;
    }
}
