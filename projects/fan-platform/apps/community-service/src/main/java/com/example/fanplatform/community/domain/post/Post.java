package com.example.fanplatform.community.domain.post;

import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.post.status.PostStatusMachine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Post aggregate root for fan-platform.
 *
 * <p>Multi-tenant: every {@code Post} carries a non-nullable {@code tenant_id}.
 * Cross-tenant reads are blocked at the repository layer (every query includes
 * {@code WHERE tenant_id = ?}).
 *
 * <p>Status transitions flow through {@link PostStatusMachine} — direct setter
 * mutation is impossible because no setter exists.
 */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "author_account_id", length = 36, nullable = false)
    private String authorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", length = 20, nullable = false)
    private PostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20, nullable = false)
    private PostVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PostStatus status;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** JSON-serialized list of S3/MinIO media keys (raw upload is v2). */
    @Column(name = "media_refs", columnDefinition = "jsonb")
    private String mediaRefsJson;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Post createDraft(String id,
                                   String tenantId,
                                   String authorAccountId,
                                   PostType postType,
                                   PostVisibility visibility,
                                   String title,
                                   String body,
                                   String mediaRefsJson) {
        Post p = new Post();
        p.id = id;
        p.tenantId = tenantId;
        p.authorAccountId = authorAccountId;
        p.postType = postType;
        p.visibility = visibility;
        p.status = PostStatus.DRAFT;
        p.title = title;
        p.body = body;
        p.mediaRefsJson = mediaRefsJson;
        Instant now = Instant.now();
        p.createdAt = now;
        p.updatedAt = now;
        // Leave version null so Spring Data JPA save() detects this as a new
        // entity and calls persist() instead of merge().
        return p;
    }

    public void publish(ActorType actor) {
        PostStatusMachine.ensureTransitionAllowed(this.status, PostStatus.PUBLISHED, actor);
        this.status = PostStatus.PUBLISHED;
        Instant now = Instant.now();
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
        this.updatedAt = now;
    }

    /**
     * Transitions the post to {@code target}. Returns the previous status so the
     * caller can persist a history entry. Validates the transition through the
     * state machine — invalid transitions throw {@link
     * com.example.fanplatform.community.domain.post.status.InvalidStateTransitionException}.
     */
    public PostStatus changeStatus(PostStatus target, ActorType actor) {
        PostStatus previous = this.status;
        PostStatusMachine.ensureTransitionAllowed(previous, target, actor);
        this.status = target;
        Instant now = Instant.now();
        this.updatedAt = now;
        if (target == PostStatus.DELETED) {
            this.deletedAt = now;
        }
        if (target == PostStatus.PUBLISHED && this.publishedAt == null) {
            this.publishedAt = now;
        }
        return previous;
    }

    public void updateContent(String title, String body, String mediaRefsJson) {
        if (this.status == PostStatus.DELETED) {
            throw new IllegalStateException("Cannot edit a DELETED post");
        }
        this.title = title;
        this.body = body;
        this.mediaRefsJson = mediaRefsJson;
        this.updatedAt = Instant.now();
    }
}
