package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.SellerInvitation;
import com.example.product.domain.model.SellerInvitationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;

/** JPA mapping of {@code seller_member_invitations} (TASK-MONO-752, V22 / h2 V15). Only the token's SHA-256 is stored. */
@Entity
@Table(name = "seller_member_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerInvitationJpaEntity implements Persistable<String> {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "seller_id", nullable = false, updatable = false, length = 64)
    private String sellerId;

    @Column(name = "email", nullable = false, updatable = false, length = 320)
    private String email;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerInvitationStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "invited_by", updatable = false, length = 64)
    private String invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_account_id", length = 64)
    private String acceptedAccountId;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public static SellerInvitationJpaEntity from(SellerInvitation invitation, String tenantId) {
        SellerInvitationJpaEntity entity = new SellerInvitationJpaEntity();
        entity.id = invitation.getId();
        entity.tenantId = tenantId;
        entity.sellerId = invitation.getSellerId();
        entity.email = invitation.getEmail();
        entity.tokenHash = invitation.getTokenHash();
        entity.status = invitation.getStatus();
        entity.expiresAt = invitation.getExpiresAt();
        entity.invitedBy = invitation.getInvitedBy();
        entity.createdAt = invitation.getCreatedAt();
        entity.acceptedAt = invitation.getAcceptedAt();
        entity.acceptedAccountId = invitation.getAcceptedAccountId();
        entity.isNew = true;
        return entity;
    }

    public SellerInvitation toDomain() {
        return SellerInvitation.reconstitute(id, sellerId, email, tokenHash, status, expiresAt, invitedBy,
                createdAt, acceptedAt, acceptedAccountId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellerInvitationJpaEntity e)) return false;
        return Objects.equals(id, e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
