package com.example.admin.application;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-772 S3 — what the IdP acceptance page shows before anything is written (auth-to-admin.md § preview).
 * Never the address itself (only {@code maskedEmail}), never the token or its hash.
 *
 * @param tenantDisplayName {@code null} when the tenant read failed (the preview is fail-soft, not a verdict)
 */
public record OperatorInvitationPreviewResult(
        String tenantId,
        String tenantDisplayName,
        String maskedEmail,
        List<String> roles,
        String status,
        boolean expired,
        Instant expiresAt
) {
}
