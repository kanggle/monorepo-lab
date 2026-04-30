package com.example.community.infrastructure.persistence;

import com.example.community.domain.post.status.PostStatusHistoryEntry;
import com.example.community.domain.post.status.PostStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostStatusHistoryRepositoryAdapter implements PostStatusHistoryRepository {

    private final PostStatusHistoryJpaRepository jpaRepository;

    @Override
    public void save(PostStatusHistoryEntry entry) {
        jpaRepository.save(PostStatusHistoryJpaEntity.record(
                entry.postId(),
                entry.fromStatus().name(),
                entry.toStatus().name(),
                entry.actorType().name(),
                entry.actorId(),
                entry.reason()
        ));
    }
}
