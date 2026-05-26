package com.wms.inventory.adapter.in.web;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Inbound adapter-local static utility for {@code actorId} extraction from
 * an authenticated {@link Jwt}. Extracted from the 3 inventory controllers
 * (AdjustmentController / ReservationController / TransferController) to
 * eliminate the byte-identical 4-line method duplicated across all three
 * (TASK-BE-301 Cohort A closure).
 *
 * <p>Adapter-local (not a {@code libs/} candidate per
 * {@code platform/shared-library-policy.md}): the JWT subject convention is
 * inventory-service-specific (Spring Security OAuth2 resource-server +
 * {@code actorId} claim fallback).
 */
public final class JwtHelper {

    private JwtHelper() {
        // utility class — no instances
    }

    /**
     * Returns the principal-actor identifier for an authenticated request.
     *
     * @param jwt the authenticated JWT, or {@code null} for unauthenticated paths
     * @return {@code "anonymous"} when {@code jwt} is {@code null}; otherwise
     *         the JWT {@code subject} when present, else the
     *         {@code actorId} claim string
     */
    public static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
