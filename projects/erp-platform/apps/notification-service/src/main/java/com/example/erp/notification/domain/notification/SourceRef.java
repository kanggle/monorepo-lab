package com.example.erp.notification.domain.notification;

import java.util.Objects;

/**
 * Back-reference to the originating fact (VO). Sources: an approval request
 * ({@code sourceType = APPROVAL}, {@code sourceId = approvalRequestId}) or a
 * delegation grant ({@code sourceType = DELEGATION}, {@code sourceId = grantId},
 * TASK-ERP-BE-014). The authoritative state lives in {@code approval-service}
 * (E5) — this is an opaque back-reference, never a reconstructed master fact.
 */
public record SourceRef(SourceType sourceType, String sourceId) {

    public SourceRef {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
    }

    public static SourceRef approval(String approvalRequestId) {
        return new SourceRef(SourceType.APPROVAL, approvalRequestId);
    }

    /** Back-reference to a delegation grant (TASK-ERP-BE-014). */
    public static SourceRef delegation(String grantId) {
        return new SourceRef(SourceType.DELEGATION, grantId);
    }

    /** Source families. {@code APPROVAL} + {@code DELEGATION} (TASK-ERP-BE-014). */
    public enum SourceType {
        APPROVAL,
        DELEGATION
    }
}
