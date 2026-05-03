package com.example.fanplatform.community.infrastructure.jpa;

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

@Entity
@Table(name = "post_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "from_status", length = 20, nullable = false)
    private String fromStatus;

    @Column(name = "to_status", length = 20, nullable = false)
    private String toStatus;

    @Column(name = "actor_type", length = 20, nullable = false)
    private String actorType;

    @Column(name = "actor_account_id", length = 36)
    private String actorAccountId;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static PostStatusHistoryJpaEntity record(String postId, String tenantId,
                                                    String fromStatus, String toStatus,
                                                    String actorType, String actorAccountId,
                                                    String reason, Instant occurredAt) {
        PostStatusHistoryJpaEntity h = new PostStatusHistoryJpaEntity();
        h.postId = postId;
        h.tenantId = tenantId;
        h.fromStatus = fromStatus;
        h.toStatus = toStatus;
        h.actorType = actorType;
        h.actorAccountId = actorAccountId;
        h.reason = reason;
        h.occurredAt = occurredAt;
        return h;
    }
}
