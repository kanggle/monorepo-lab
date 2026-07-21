package com.example.fanplatform.membership.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated end-user JWT. Passed to
 * use cases as a value object so Spring Security types stay out of the
 * application layer. {@code accountId} = the {@code sub} claim.
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {
}
