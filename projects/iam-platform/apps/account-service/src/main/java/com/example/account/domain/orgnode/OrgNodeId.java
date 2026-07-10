package com.example.account.domain.orgnode;

import java.util.UUID;

/**
 * TASK-BE-491 (ADR-MONO-047 § D1): opaque identifier of an {@link OrgNode}.
 *
 * <p>A UUID, not a human-readable slug. The node's {@code name} is a company/division
 * display name and is deliberately <b>not unique</b> — two companies may share a name;
 * only ids identify.
 */
public record OrgNodeId(String value) {

    public OrgNodeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("orgNodeId must not be blank");
        }
        if (value.length() > 36) {
            throw new IllegalArgumentException("orgNodeId must not exceed 36 characters");
        }
    }

    public static OrgNodeId generate() {
        return new OrgNodeId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
