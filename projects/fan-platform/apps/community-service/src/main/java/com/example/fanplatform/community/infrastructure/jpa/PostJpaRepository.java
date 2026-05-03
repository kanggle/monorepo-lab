package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.post.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostJpaRepository extends JpaRepository<Post, String> {

    Optional<Post> findByIdAndTenantId(String id, String tenantId);

    @Query("""
            SELECT p FROM Post p
            WHERE p.tenantId = :tenantId
              AND p.authorAccountId IN (
                  SELECT f.artistAccountId FROM Follow f
                  WHERE f.fanAccountId = :fanAccountId
                    AND f.tenantId = :tenantId
              )
              AND p.status = com.example.fanplatform.community.domain.post.status.PostStatus.PUBLISHED
              AND p.deletedAt IS NULL
            ORDER BY p.publishedAt DESC
            """)
    Page<Post> findFeedForFan(@Param("fanAccountId") String fanAccountId,
                              @Param("tenantId") String tenantId,
                              Pageable pageable);
}
