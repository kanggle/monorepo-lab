package com.example.auth.application.port;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-772 S3 (auth-to-admin.md § preview · § accept) — the two admin-service calls behind the IdP page
 * {@code /operator-invitations/accept}. Every answer is a value: the page maps each one to a screen
 * (auth-api.md § IdP 브라우저 화면 — 운영자 초대 수락), so nothing here throws for a refusal or an outage.
 *
 * <p>🔴 R4: the token is passed through, never logged, never stored.
 */
public interface OperatorInvitationAcceptancePort {

    enum PreviewOutcome { FOUND, NOT_FOUND, UNAVAILABLE }

    /** What the page draws. {@code tenantDisplayName} may be {@code null} (admin's tenant read is fail-soft). */
    record Preview(String tenantId, String tenantDisplayName, String maskedEmail, List<String> roles,
                   String status, boolean expired, Instant expiresAt) {

        /** The name the page shows for the company — its display name, else its id. */
        public String companyName() {
            return tenantDisplayName != null && !tenantDisplayName.isBlank() ? tenantDisplayName : tenantId;
        }
    }

    /** {@code preview} is set only for {@link PreviewOutcome#FOUND}. */
    record PreviewResult(PreviewOutcome outcome, Preview preview) {
        public static PreviewResult notFound() {
            return new PreviewResult(PreviewOutcome.NOT_FOUND, null);
        }

        public static PreviewResult unavailable() {
            return new PreviewResult(PreviewOutcome.UNAVAILABLE, null);
        }
    }

    /** admin-service's answers (auth-to-admin.md § accept Errors), plus «no answer». */
    enum AcceptOutcome {
        ACCEPTED,
        ALREADY_ACCEPTED,
        EMAIL_NOT_VERIFIED,
        EMAIL_MISMATCH,
        ACCOUNT_NOT_ELIGIBLE,
        NOT_FOUND,
        ALREADY_USED,
        ALREADY_PROVISIONED,
        EMAIL_CONFLICT,
        INVALIDATED,
        EXPIRED,
        /** 5xx · timeout · circuit open · an unknown code · an unreadable body — nothing was written. */
        UNAVAILABLE
    }

    /** {@code tenantId} is set for {@link AcceptOutcome#ACCEPTED} / {@link AcceptOutcome#ALREADY_ACCEPTED}. */
    record AcceptResult(AcceptOutcome outcome, String tenantId) {}

    PreviewResult preview(String token);

    /**
     * @param accountId 🔴 the IdP session principal's account id — the caller takes it from the session, never
     *                  from the form (auth-api.md)
     */
    AcceptResult accept(String token, String accountId);
}
