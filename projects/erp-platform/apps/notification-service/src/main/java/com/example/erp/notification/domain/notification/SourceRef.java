package com.example.erp.notification.domain.notification;

import java.util.Objects;

/**
 * Back-reference to the originating fact (VO). In v1 the only source is an
 * approval request: {@code sourceType = APPROVAL}, {@code sourceId =
 * approvalRequestId}. The authoritative approval state lives in
 * {@code approval-service} (E5) — this is an opaque back-reference, never a
 * reconstructed master fact.
 */
public record SourceRef(SourceType sourceType, String sourceId) {

    public SourceRef {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
    }

    public static SourceRef approval(String approvalRequestId) {
        return new SourceRef(SourceType.APPROVAL, approvalRequestId);
    }

    /** Source families. {@code APPROVAL} only in v1; a v2 increment adds more. */
    public enum SourceType {
        APPROVAL
    }
}
