package com.wms.notification.domain.error;

/**
 * Sealed root of every domain-level exception in notification-service.
 *
 * <p>Subtypes correspond to error codes registered in
 * {@code platform/error-handling.md} (Open Items). Five v1 codes:
 *
 * <ul>
 *   <li>{@code DELIVERY_RETRY_EXHAUSTED}</li>
 *   <li>{@code DELIVERY_STATE_TRANSITION_INVALID}</li>
 *   <li>{@code IDEMPOTENCY_KEY_DUPLICATE}</li>
 *   <li>{@code ROUTING_AMBIGUOUS}</li>
 *   <li>{@code ROUTING_RULE_NOT_FOUND}</li>
 * </ul>
 */
public sealed class NotificationDomainException extends RuntimeException
        permits DeliveryRetryExhaustedException,
                DeliveryStateTransitionInvalidException,
                IdempotencyKeyDuplicateException,
                RoutingAmbiguousException,
                RoutingRuleNotFoundException {

    private final String code;

    protected NotificationDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected NotificationDomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
