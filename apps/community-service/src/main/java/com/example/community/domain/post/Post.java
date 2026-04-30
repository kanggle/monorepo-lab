package com.example.community.domain.post;

import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.domain.post.status.PostStatusMachine;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "author_account_id", length = 36, nullable = false)
    private String authorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private PostType type;

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

    @Column(name = "media_urls", columnDefinition = "JSON")
    private String mediaUrlsJson;

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
    private Integer version;

    public static Post createDraft(String authorAccountId,
                                   PostType type,
                                   PostVisibility visibility,
                                   String title,
                                   String body,
                                   String mediaUrlsJson) {
        Post p = new Post();
        p.id = UUID.randomUUID().toString();
        p.authorAccountId = authorAccountId;
        p.type = type;
        p.visibility = visibility;
        p.status = PostStatus.DRAFT;
        p.title = title;
        p.body = body;
        p.mediaUrlsJson = mediaUrlsJson;
        Instant now = Instant.now();
        p.createdAt = now;
        p.updatedAt = now;
        p.version = 0;
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

    public void updateContent(String title, String body, String mediaUrlsJson) {
        if (this.status == PostStatus.DELETED) {
            throw new IllegalStateException("STATE_TRANSITION_INVALID");
        }
        this.title = title;
        this.body = body;
        this.mediaUrlsJson = mediaUrlsJson;
        this.updatedAt = Instant.now();
    }
}
