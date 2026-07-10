package com.example.account.infrastructure.persistence;

import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TASK-BE-491 (ADR-MONO-047 § D1/D2): {@code org_node} row mapping.
 *
 * <p>The ceiling is persisted as two columns rather than one, so that
 * {@code UNBOUNDED} (the intersection identity) and {@code BOUNDED(∅)} (nothing
 * permitted) — which are opposites — cannot be conflated at the storage layer. See
 * {@link EntitlementCeiling}.
 */
@Entity
@Table(name = "org_node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrgNodeJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    /** {@code null} for a root node. */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "ceiling_mode", length = 16, nullable = false)
    private String ceilingMode;

    @Column(name = "ceiling_domains", length = 255, nullable = false)
    private String ceilingDomains;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OrgNodeJpaEntity fromDomain(OrgNode node) {
        OrgNodeJpaEntity entity = new OrgNodeJpaEntity();
        entity.id = node.getId().value();
        entity.parentId = node.getParentId() == null ? null : node.getParentId().value();
        entity.name = node.getName();
        entity.ceilingMode = node.getCeiling().mode().name();
        entity.ceilingDomains = node.getCeiling().domainsCsv();
        entity.depth = node.getDepth();
        entity.createdAt = node.getCreatedAt();
        entity.updatedAt = node.getUpdatedAt();
        return entity;
    }

    public OrgNode toDomain() {
        return OrgNode.reconstitute(
                new OrgNodeId(id),
                parentId == null ? null : new OrgNodeId(parentId),
                name,
                EntitlementCeiling.fromStorage(ceilingMode, ceilingDomains),
                depth,
                createdAt,
                updatedAt
        );
    }
}
