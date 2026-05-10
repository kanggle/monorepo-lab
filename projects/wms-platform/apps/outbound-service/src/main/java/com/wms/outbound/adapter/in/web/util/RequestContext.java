package com.wms.outbound.adapter.in.web.util;

import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class RequestContext {

    private RequestContext() {
    }

    public static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
    }

    public static Set<String> callerRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : authentication.getAuthorities()) {
            roles.add(a.getAuthority());
        }
        return Set.copyOf(roles);
    }

    public static String actorId(Jwt jwt) {
        if (jwt == null) {
            return "anonymous";
        }
        return jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("actorId");
    }
}
