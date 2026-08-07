package com.example.scmplatform.procurement.domain.error;

/**
 * Mapped to HTTP 403 {@code PERMISSION_DENIED}.
 *
 * <p>Until TASK-SCM-BE-059 this service emitted {@code PERMISSION_DENIED} only
 * from the security filter chain (an unauthenticated/denied request never
 * reached a controller). Supplier registration is the first endpoint whose
 * authorization is a use-case decision — the caller is authenticated and
 * tenant-admitted, and is refused on its actor role — so the code now has a
 * second, application-layer emitter. Same registry row, same envelope.
 */
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) {
        super(message);
    }
}
