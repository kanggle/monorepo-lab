package com.example.community.infrastructure.persistence;

import com.example.community.domain.comment.Comment;
import com.example.community.domain.comment.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CommentRepositoryAdapter implements CommentRepository {

    private final CommentJpaRepository commentJpaRepository;

    @Override
    public Comment save(Comment comment) {
        return commentJpaRepository.save(comment);
    }

    @Override
    public long countByPostId(String postId) {
        return commentJpaRepository.countByPostIdAndDeletedAtIsNull(postId);
    }

    @Override
    public Map<String, Long> countsByPostIds(List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return commentJpaRepository.countsGroupedByPostId(postIds).stream()
                .collect(Collectors.toMap(
                        CommentJpaRepository.PostIdCount::getPostId,
                        CommentJpaRepository.PostIdCount::getCnt));
    }
}
