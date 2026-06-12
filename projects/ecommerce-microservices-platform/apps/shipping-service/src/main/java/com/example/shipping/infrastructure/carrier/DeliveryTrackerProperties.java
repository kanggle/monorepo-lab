package com.example.shipping.infrastructure.carrier;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Delivery Tracker (tracker.delivery) outbound integration config (TASK-BE-364 /
 * external-integrations.md § 1.1–1.2). The concrete logistics aggregator behind the
 * provider-agnostic {@code shipping.carrier} port (ADR-007 D2). Bound from
 * {@code shipping.carrier.delivery-tracker.*}; every value is env-injected with a blank
 * default for the credentials — <b>no hardcoded secret</b>. A blank {@code clientId} or
 * {@code clientSecret} disables the adapter (net-zero, identical to {@code mode=mock};
 * ADR-007 D4).
 *
 * <p>Only active when {@code shipping.carrier.mode=delivery-tracker}
 * (see {@link DeliveryTrackerCarrierTrackingAdapter}); the default {@code mode=mock} never
 * binds this type into a live call path.
 *
 * @param authUrl    OAuth2 token endpoint (default {@code https://auth.tracker.delivery/oauth2/token})
 * @param graphqlUrl GraphQL tracking endpoint (default {@code https://apis.tracker.delivery/graphql})
 * @param clientId   OAuth2 client_credentials client id (env-injected, blank = disabled)
 * @param clientSecret OAuth2 client_credentials secret (env-injected, blank = disabled)
 */
@ConfigurationProperties(prefix = "shipping.carrier.delivery-tracker")
public record DeliveryTrackerProperties(
        String authUrl,
        String graphqlUrl,
        String clientId,
        String clientSecret) {

    /** True only when both credentials are present — otherwise the adapter is a net-zero no-op. */
    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
