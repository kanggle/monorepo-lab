package com.example.notification.domain.model;

import com.example.notification.domain.tenant.TenantContext;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A browser Web Push subscription owned by a user (TASK-BE-464). Unlike email, a push
 * recipient is a per-browser subscription — the push service {@code endpoint} URL plus the
 * client's {@code p256dh}/{@code auth} key pair used to encrypt the payload. A user may hold
 * several (one per browser/device).
 *
 * <p>Created on the HTTP register path, so the owning tenant is taken from
 * {@link TenantContext} (gateway-injected {@code X-Tenant-Id}); an unset context resolves to
 * the default tenant (D8 net-zero), mirroring {@code NotificationTemplate.create}.
 */
public class PushSubscription {

    private String subscriptionId;
    private String tenantId;
    private String userId;
    private String endpoint;
    private String p256dh;
    private String auth;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private PushSubscription() {
    }

    public static PushSubscription register(String userId, String endpoint, String p256dh, String auth,
                                            String userAgent) {
        requireText(userId, "userId");
        requireText(endpoint, "endpoint");
        requireText(p256dh, "p256dh");
        requireText(auth, "auth");

        PushSubscription subscription = new PushSubscription();
        subscription.subscriptionId = UUID.randomUUID().toString();
        subscription.tenantId = TenantContext.currentTenant();
        subscription.userId = userId;
        subscription.endpoint = endpoint;
        subscription.p256dh = p256dh;
        subscription.auth = auth;
        // User-Agent is a best-effort device label, not domain-critical: an absent or blank
        // header is stored as null so the UI shows "unknown device" rather than an empty string.
        subscription.userAgent = (userAgent == null || userAgent.isBlank()) ? null : userAgent;
        subscription.createdAt = LocalDateTime.now();
        subscription.updatedAt = LocalDateTime.now();
        return subscription;
    }

    public static PushSubscription reconstitute(String subscriptionId, String tenantId, String userId,
                                                String endpoint, String p256dh, String auth,
                                                String userAgent,
                                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        PushSubscription subscription = new PushSubscription();
        subscription.subscriptionId = subscriptionId;
        subscription.tenantId = tenantId;
        subscription.userId = userId;
        subscription.endpoint = endpoint;
        subscription.p256dh = p256dh;
        subscription.auth = auth;
        subscription.userAgent = userAgent;
        subscription.createdAt = createdAt;
        subscription.updatedAt = updatedAt;
        return subscription;
    }

    /** Re-registration of the same endpoint rotates the client keys. */
    public void updateKeys(String p256dh, String auth) {
        requireText(p256dh, "p256dh");
        requireText(auth, "auth");
        this.p256dh = p256dh;
        this.auth = auth;
        this.updatedAt = LocalDateTime.now();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
