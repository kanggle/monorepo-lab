package com.example.account.application.event;

import com.example.common.id.UuidV7;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * TASK-BE-342 (ADR-MONO-023 D4): outbox publisher for
 * {@code tenant.subscription.changed} — emitted on every subscription lifecycle
 * mutation (subscribe / suspend / resume / cancel).
 *
 * <p>This is the <em>entitlement-plane</em> change notification. Per ADR-023 D2
 * the event is fire-and-forget for asynchronous consumers (console cache
 * invalidation, future billing/notification); the catalog (ADR-019 D4) and
 * {@code entitled_domains} (ADR-019 D5) read paths already filter ACTIVE, so a
 * suspend/cancel is reflected at the next read/issuance with no extra wiring.
 * Consumers MUST NOT mutate the IAM plane (operator assignments / RBAC) from
 * this event — those bindings are preserved across an entitlement change.
 *
 * <p>Unlike the account.* events (aggregate {@code "Account"}, partition key
 * {@code account_id}), this event's aggregate is the subscription:
 * {@code aggregate_type = "TenantDomainSubscription"},
 * {@code aggregate_id = "<tenantId>:<domainKey>"}.
 *
 * <p>{@code @Transactional} (REQUIRED) so the outbox write participates in the
 * mutation use-case's transaction (same rationale as {@link AccountEventPublisher}).
 */
@Component
public class TenantDomainSubscriptionEventPublisher extends BaseEventPublisher {

    public static final String EVENT_TYPE = "tenant.subscription.changed";
    private static final String AGGREGATE_TYPE = "TenantDomainSubscription";

    public TenantDomainSubscriptionEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    /**
     * @param previousStatus the prior status name, or {@code null} for a brand-new
     *                        subscribe (create)
     */
    @Transactional
    public void publishSubscriptionChanged(String tenantId, String domainKey,
                                           String previousStatus, String currentStatus,
                                           String reason, String actorType, String actorId,
                                           Instant occurredAt) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(domainKey)) {
            throw new IllegalArgumentException("tenantId and domainKey required");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UuidV7.randomString());
        payload.put("tenantId", tenantId);
        payload.put("domainKey", domainKey);
        payload.put("previousStatus", previousStatus); // null on create (allowed)
        payload.put("currentStatus", currentStatus);
        payload.put("actorType", actorType != null ? actorType : "operator");
        payload.put("occurredAt", occurredAt.toString());
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        saveEvent(AGGREGATE_TYPE, tenantId + ":" + domainKey, EVENT_TYPE, payload);
    }
}
