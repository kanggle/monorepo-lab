package com.example.community.infrastructure.persistence;

import jakarta.persistence.*;
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

    @Column(name = "from_status", length = 20, nullable = false)
    private String fromStatus;

    @Column(name = "to_status", length = 20, nullable = false)
    private String toStatus;

    @Column(name = "actor_type", length = 20, nullable = false)
    private String actorType;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * Convenience factory that stamps the current instant as {@code occurredAt}.
     * Used by persistence-layer tests that only exercise queries/ordering. The
     * production mapping ({@code PostStatusHistoryRepositoryImpl}) uses the
     * {@code occurredAt}-carrying overload so the domain event time is preserved.
     */
    public static PostStatusHistoryJpaEntity record(String postId, String fromStatus, String toStatus,
                                                    String actorType, String actorId, String reason) {
        return record(postId, fromStatus, toStatus, actorType, actorId, reason, Instant.now());
    }

    /**
     * Factory that persists the supplied {@code occurredAt} (the domain event time)
     * rather than re-stamping it at save time.
     */
    public static PostStatusHistoryJpaEntity record(String postId, String fromStatus, String toStatus,
                                                    String actorType, String actorId, String reason,
                                                    Instant occurredAt) {
        PostStatusHistoryJpaEntity h = new PostStatusHistoryJpaEntity();
        h.postId = postId;
        h.fromStatus = fromStatus;
        h.toStatus = toStatus;
        h.actorType = actorType;
        h.actorId = actorId;
        h.reason = reason;
        h.occurredAt = occurredAt;
        return h;
    }
}
