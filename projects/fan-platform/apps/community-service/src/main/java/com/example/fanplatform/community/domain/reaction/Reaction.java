package com.example.fanplatform.community.domain.reaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Reaction aggregate. Composite PK = (post_id, reactor_account_id). A second
 * call from the same reactor mutates {@code reactionType} in place — there is
 * never more than one row per (post, reactor) tuple. tenant_id is duplicated
 * for query-scoping convenience but is logically derived from the parent post.
 */
@Entity
@Table(name = "reactions")
@IdClass(Reaction.ReactionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction {

    @Id
    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Id
    @Column(name = "reactor_account_id", length = 36, nullable = false)
    private String reactorAccountId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 20, nullable = false)
    private ReactionType reactionType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Reaction create(String postId, String reactorAccountId,
                                  String tenantId, ReactionType reactionType) {
        Reaction r = new Reaction();
        r.postId = postId;
        r.reactorAccountId = reactorAccountId;
        r.tenantId = tenantId;
        r.reactionType = reactionType;
        Instant now = Instant.now();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void changeType(ReactionType reactionType) {
        this.reactionType = reactionType;
        this.updatedAt = Instant.now();
    }

    public static class ReactionId implements Serializable {
        private String postId;
        private String reactorAccountId;

        public ReactionId() {}

        public ReactionId(String postId, String reactorAccountId) {
            this.postId = postId;
            this.reactorAccountId = reactorAccountId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReactionId that)) return false;
            return Objects.equals(postId, that.postId)
                    && Objects.equals(reactorAccountId, that.reactorAccountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, reactorAccountId);
        }
    }
}
