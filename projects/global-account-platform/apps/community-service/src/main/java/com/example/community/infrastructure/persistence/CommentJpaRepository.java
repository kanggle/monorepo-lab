package com.example.community.infrastructure.persistence;

import com.example.community.domain.comment.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentJpaRepository extends JpaRepository<Comment, String> {
    long countByPostIdAndDeletedAtIsNull(String postId);

    @Query("""
            SELECT c.postId AS postId, COUNT(c) AS cnt
            FROM Comment c
            WHERE c.postId IN :postIds AND c.deletedAt IS NULL
            GROUP BY c.postId
            """)
    List<PostIdCount> countsGroupedByPostId(@Param("postIds") Collection<String> postIds);

    interface PostIdCount {
        String getPostId();
        Long getCnt();
    }
}
