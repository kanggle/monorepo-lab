package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.reaction.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReactionJpaRepository extends JpaRepository<Reaction, Reaction.ReactionId> {

    Optional<Reaction> findByPostIdAndReactorAccountIdAndTenantId(
            String postId, String reactorAccountId, String tenantId);

    long countByPostIdAndTenantId(String postId, String tenantId);

    @Query("""
            SELECT r.postId AS postId, COUNT(r) AS cnt
            FROM Reaction r
            WHERE r.postId IN :postIds AND r.tenantId = :tenantId
            GROUP BY r.postId
            """)
    List<PostIdCount> countsGroupedByPostId(@Param("postIds") Collection<String> postIds,
                                            @Param("tenantId") String tenantId);

    interface PostIdCount {
        String getPostId();
        Long getCnt();
    }
}
