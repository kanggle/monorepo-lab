package com.example.community.domain.reaction;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

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
    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "emoji_code", length = 20, nullable = false)
    private String emojiCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Reaction create(String postId, String accountId, String emojiCode) {
        Reaction r = new Reaction();
        r.postId = postId;
        r.accountId = accountId;
        r.emojiCode = emojiCode;
        Instant now = Instant.now();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void changeEmoji(String emojiCode) {
        this.emojiCode = emojiCode;
        this.updatedAt = Instant.now();
    }

    public static class ReactionId implements Serializable {
        private String postId;
        private String accountId;

        public ReactionId() {}

        public ReactionId(String postId, String accountId) {
            this.postId = postId;
            this.accountId = accountId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReactionId that)) return false;
            return Objects.equals(postId, that.postId) && Objects.equals(accountId, that.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, accountId);
        }
    }
}
