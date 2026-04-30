package com.example.community.infrastructure.persistence;

import com.example.community.domain.reaction.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReactionJpaRepository extends JpaRepository<Reaction, Reaction.ReactionId> {

    Optional<Reaction> findByPostIdAndAccountId(String postId, String accountId);

    long countByPostId(String postId);

    @Query("""
            SELECT r.postId AS postId, COUNT(r) AS cnt
            FROM Reaction r
            WHERE r.postId IN :postIds
            GROUP BY r.postId
            """)
    List<PostIdCount> countsGroupedByPostId(@Param("postIds") Collection<String> postIds);

    interface PostIdCount {
        String getPostId();
        Long getCnt();
    }
}
