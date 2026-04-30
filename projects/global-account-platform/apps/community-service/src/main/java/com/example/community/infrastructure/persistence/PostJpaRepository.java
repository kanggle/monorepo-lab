package com.example.community.infrastructure.persistence;

import com.example.community.domain.post.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostJpaRepository extends JpaRepository<Post, String> {

    @Query("""
            SELECT p FROM Post p
            WHERE p.authorAccountId IN (
                SELECT fs.artistAccountId FROM FeedSubscription fs
                WHERE fs.fanAccountId = :fanAccountId
            )
            AND p.status = com.example.community.domain.post.status.PostStatus.PUBLISHED
            AND p.deletedAt IS NULL
            ORDER BY p.publishedAt DESC
            """)
    Page<Post> findFeedForFan(@Param("fanAccountId") String fanAccountId, Pageable pageable);
}
