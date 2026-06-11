package com.example.fanplatform.notification.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated end-user JWT. Passed to
 * use cases as a value object so Spring Security types stay out of the
 * application layer. {@code accountId} = the {@code sub} claim (the recipient
 * fan); inbox queries are always scoped to {@code accountId} + {@code tenantId}.
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
