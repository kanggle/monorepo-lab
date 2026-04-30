package com.example.community.infrastructure.persistence;

import com.example.community.domain.reaction.Reaction;
import com.example.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReactionRepositoryAdapter implements ReactionRepository {

    private final ReactionJpaRepository reactionJpaRepository;

    @Override
    public Optional<Reaction> find(String postId, String accountId) {
        return reactionJpaRepository.findByPostIdAndAccountId(postId, accountId);
    }

    @Override
    public Reaction save(Reaction reaction) {
        return reactionJpaRepository.save(reaction);
    }

    @Override
    public long countByPostId(String postId) {
        return reactionJpaRepository.countByPostId(postId);
    }

    @Override
    public Map<String, Long> countsByPostIds(List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return reactionJpaRepository.countsGroupedByPostId(postIds).stream()
                .collect(Collectors.toMap(
                        ReactionJpaRepository.PostIdCount::getPostId,
                        ReactionJpaRepository.PostIdCount::getCnt));
    }
}
