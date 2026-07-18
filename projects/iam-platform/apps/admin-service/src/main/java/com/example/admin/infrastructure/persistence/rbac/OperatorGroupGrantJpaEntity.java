package com.example.admin.infrastructure.persistence.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TASK-BE-520 / ADR-MONO-046 D5 (Flyway V0043) — JPA entity for {@code operator_group_grant}:
 * a group's grant TEMPLATE (ROLE or TENANT_ASSIGNMENT). Persisted independently of members
 * so add-member can re-fan the group's current grants and a member-0 group keeps its grants.
 * The DB CHECK pins exactly one of {@code roleId} / {@code tenantId} per {@code grantType}.
 */
@Entity
@Table(name = "operator_group_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorGroupGrantJpaEntity {

    /** Grant kinds — value byte-matches the {@code grant_type} column + admin-api.md wire enum. */
    public static final String TYPE_ROLE = "ROLE";
    public static final String TYPE_TENANT_ASSIGNMENT = "TENANT_ASSIGNMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "grant_id", length = 36, nullable = false, unique = true)
    private String grantId;

    // BIGINT FK → operator_group.id (surrogate), not the external group_id UUID.
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "grant_type", length = 20, nullable = false)
    private String grantType;

    // grant_type=ROLE: the granted role (admin_roles.id). NULL for TENANT_ASSIGNMENT.
    @Column(name = "role_id")
    private Long roleId;

    // grant_type=TENANT_ASSIGNMENT: the ASSIGNED tenant. NULL for ROLE.
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    /** Factory for a ROLE grant template. */
    public static OperatorGroupGrantJpaEntity role(String grantId, Long groupId, Long roleId,
                                                   Long grantedBy, Instant grantedAt) {
        OperatorGroupGrantJpaEntity e = new OperatorGroupGrantJpaEntity();
        e.grantId = grantId;
        e.groupId = groupId;
        e.grantType = TYPE_ROLE;
        e.roleId = roleId;
        e.grantedBy = grantedBy;
        e.grantedAt = grantedAt;
        return e;
    }

    /** Factory for a TENANT_ASSIGNMENT grant template. */
    public static OperatorGroupGrantJpaEntity tenantAssignment(String grantId, Long groupId, String tenantId,
                                                               Long grantedBy, Instant grantedAt) {
        OperatorGroupGrantJpaEntity e = new OperatorGroupGrantJpaEntity();
        e.grantId = grantId;
        e.groupId = groupId;
        e.grantType = TYPE_TENANT_ASSIGNMENT;
        e.tenantId = tenantId;
        e.grantedBy = grantedBy;
        e.grantedAt = grantedAt;
        return e;
    }

    public boolean isRole() {
        return TYPE_ROLE.equals(grantType);
    }
}
