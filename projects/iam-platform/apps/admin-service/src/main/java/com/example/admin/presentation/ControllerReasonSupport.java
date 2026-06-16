package com.example.admin.presentation;

import com.example.admin.application.exception.ReasonRequiredException;

/**
 * Shared validation for the mandatory {@code X-Operator-Reason} header on operator
 * command endpoints. Returns the reason when present, throwing
 * {@link ReasonRequiredException} when null or blank.
 *
 * <p>Centralises the {@code requireReason} guard that was previously copied verbatim
 * across the operator command controllers. The {@code resolveReason(header, body)}
 * helpers in {@code AccountAdminController} / {@code AdminGdprController} are left in
 * place because they differ (header URL-decoding) and are not pure duplicates.
 */
final class ControllerReasonSupport {

    private ControllerReasonSupport() {
    }

    static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException();
        }
        return reason;
    }
}
