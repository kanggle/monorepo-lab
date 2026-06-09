package com.example.fanplatform.membership.presentation.dto;

/**
 * Internal access-check response: {@code { "allowed": <boolean> }}. {@code allowed}
 * maps 1:1 to the {@code MembershipChecker.hasAccess(...)} boolean return value.
 * This response is NOT wrapped in the {@code { data, meta }} envelope (the
 * contract specifies the bare {@code { allowed }} body).
 */
public record AccessResponse(boolean allowed) {
}
