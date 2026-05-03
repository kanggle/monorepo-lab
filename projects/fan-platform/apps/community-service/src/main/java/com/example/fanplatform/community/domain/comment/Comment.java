package com.example.fanplatform.community.domain.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Comment create(String id, String tenantId, String postId,
                                 String authorAccountId, String body) {
        Comment c = new Comment();
        c.id = id;
        c.tenantId = tenantId;
        c.postId = postId;
        c.authorAccountId = authorAccountId;
        c.body = body;
        c.createdAt = Instant.now();
        return c;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }
}
