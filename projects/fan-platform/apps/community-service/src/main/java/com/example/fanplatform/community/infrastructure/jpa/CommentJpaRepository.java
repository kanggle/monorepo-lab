package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.comment.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentJpaRepository extends JpaRepository<Comment, String> {

    Optional<Comment> findByIdAndTenantId(String id, String tenantId);

    long countByPostIdAndTenantIdAndDeletedAtIsNull(String postId, String tenantId);

    @Query("""
            SELECT c.postId AS postId, COUNT(c) AS cnt
            FROM Comment c
            WHERE c.postId IN :postIds AND c.tenantId = :tenantId AND c.deletedAt IS NULL
            GROUP BY c.postId
            """)
    List<PostIdCount> countsGroupedByPostId(@Param("postIds") Collection<String> postIds,
                                            @Param("tenantId") String tenantId);

    interface PostIdCount {
        String getPostId();
        Long getCnt();
    }
}
