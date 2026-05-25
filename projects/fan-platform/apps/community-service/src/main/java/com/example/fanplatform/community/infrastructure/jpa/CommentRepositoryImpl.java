package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    private final CommentJpaRepository jpa;

    @Override
    public Comment save(Comment comment) {
        return jpa.save(comment);
    }

    @Override
    public Optional<Comment> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public long countByPostId(String postId, String tenantId) {
        return jpa.countByPostIdAndTenantIdAndDeletedAtIsNull(postId, tenantId);
    }

    @Override
    public Map<String, Long> countsByPostIds(List<String> postIds, String tenantId) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return jpa.countsGroupedByPostId(postIds, tenantId).stream()
                .collect(Collectors.toMap(
                        CommentJpaRepository.PostIdCount::getPostId,
                        CommentJpaRepository.PostIdCount::getCnt));
    }
}
