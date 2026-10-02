package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.SellerMember;
import com.example.product.domain.model.SellerMemberRole;
import com.example.product.domain.model.SellerMemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** JPA mapping of {@code seller_members} (TASK-MONO-752, V22 / h2 V15). Key {@code (tenant_id, seller_id, account_id)}. */
@Entity
@Table(name = "seller_members")
@IdClass(SellerMemberJpaEntity.MemberId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerMemberJpaEntity implements Persistable<SellerMemberJpaEntity.MemberId> {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "seller_id", nullable = false, updatable = false, length = 64)
    private String sellerId;

    @Id
    @Column(name = "account_id", nullable = false, updatable = false, length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SellerMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

    @Override
    public MemberId getId() {
        return new MemberId(tenantId, sellerId, accountId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public static SellerMemberJpaEntity from(SellerMember member, String tenantId) {
        SellerMemberJpaEntity entity = new SellerMemberJpaEntity();
        entity.tenantId = tenantId;
        entity.sellerId = member.getSellerId();
        entity.accountId = member.getAccountId();
        entity.role = member.getRole();
        entity.status = member.getStatus();
        entity.joinedAt = member.getJoinedAt();
        entity.isNew = true;
        return entity;
    }

    /** Re-activates a previously REVOKED row of the same person (a new join). */
    public void reactivate(Instant joinedAt) {
        this.status = SellerMemberStatus.ACTIVE;
        this.joinedAt = joinedAt;
    }

    public void revoke() {
        this.status = SellerMemberStatus.REVOKED;
    }

    public SellerMember toDomain() {
        return SellerMember.reconstitute(sellerId, accountId, role, status, joinedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellerMemberJpaEntity e)) return false;
        return Objects.equals(tenantId, e.tenantId) && Objects.equals(sellerId, e.sellerId)
                && Objects.equals(accountId, e.accountId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Composite identity {@code (tenant_id, seller_id, account_id)}. */
    public record MemberId(String tenantId, String sellerId, String accountId) implements Serializable {
    }
}
