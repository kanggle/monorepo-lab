package com.wms.notification.domain.error;

/**
 * {@code delivery_idempotency_key} UNIQUE constraint violation under
 * concurrent routing. Storage-level invariant; surfaces only on race.
 */
public final class IdempotencyKeyDuplicateException extends NotificationDomainException {

    public static final String CODE = "IDEMPOTENCY_KEY_DUPLICATE";

    private final String idempotencyKey;

    public IdempotencyKeyDuplicateException(String idempotencyKey, Throwable cause) {
        super(CODE, "Duplicate delivery idempotency key: " + idempotencyKey, cause);
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
