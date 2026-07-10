package com.example.admin.application.orgnode;

import java.time.Instant;

/**
 * TASK-BE-492 (ADR-MONO-047 D1) — an org-node as admin-service sees it: a <b>data-less
 * grouping node above {@code tenant}</b>. It groups tenants; it never nests them, so the
 * M1 single flat isolation key is untouched and a token still carries exactly one
 * {@code tenant_id}.
 *
 * <p>admin-service does not persist this — account-service owns {@code tenants} and
 * therefore {@code org_node} (D6). This is a read-through view of the authority.
 *
 * @param parentId {@code null} ⇒ ROOT node (depth 1)
 * @param depth    root = 1, max 5 (D4)
 */
public record OrgNodeView(
        String orgNodeId,
        String parentId,
        String name,
        int depth,
        CeilingView ceiling,
        Instant createdAt,
        Instant updatedAt
) {
}
