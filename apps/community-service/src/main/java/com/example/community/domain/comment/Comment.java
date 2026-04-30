package com.example.community.domain.comment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "post_id", length = 36, nullable = false)
    private String postId;

    @Column(name = "author_account_id", length = 36, nullable = false)
    private String authorAccountId;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Comment create(String postId, String authorAccountId, String body) {
        Comment c = new Comment();
        c.id = UUID.randomUUID().toString();
        c.postId = postId;
        c.authorAccountId = authorAccountId;
        c.body = body;
        c.createdAt = Instant.now();
        return c;
    }
}
