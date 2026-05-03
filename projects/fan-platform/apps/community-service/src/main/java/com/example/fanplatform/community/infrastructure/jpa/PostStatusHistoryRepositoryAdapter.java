package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.post.status.PostStatusHistoryEntry;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostStatusHistoryRepositoryAdapter implements PostStatusHistoryRepository {

    private final PostStatusHistoryJpaRepository jpa;

    @Override
    public void save(PostStatusHistoryEntry entry) {
        jpa.save(PostStatusHistoryJpaEntity.record(
                entry.postId(),
                entry.tenantId(),
                entry.fromStatus().name(),
                entry.toStatus().name(),
                entry.actorType().name(),
                entry.actorId(),
                entry.reason(),
                entry.occurredAt()
        ));
    }
}
