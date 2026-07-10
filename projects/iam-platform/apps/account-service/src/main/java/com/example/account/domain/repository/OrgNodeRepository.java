package com.example.account.domain.repository;

import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeId;

import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-491 (ADR-MONO-047 § D1): port for the org-node grouping tree.
 *
 * <p>account-service owns {@code tenants}, therefore it owns {@code org_node} and every
 * write invariant (cycle, depth, {@code child ⊆ parent}). admin-service proxies as a thin
 * command gateway and must not duplicate these rules.
 */
public interface OrgNodeRepository {

    Optional<OrgNode> findById(OrgNodeId id);

    /** The whole tree, flat, ordered by depth then id for deterministic projection. */
    List<OrgNode> findAll();

    /** Direct children of {@code parentId}. */
    List<OrgNode> findByParentId(OrgNodeId parentId);

    boolean hasChildren(OrgNodeId id);

    OrgNode save(OrgNode node);

    void deleteById(OrgNodeId id);
}
