package com.example.fanplatform.community.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostStatusHistoryJpaRepository extends JpaRepository<PostStatusHistoryJpaEntity, Long> {
    List<PostStatusHistoryJpaEntity> findByPostIdAndTenantIdOrderByOccurredAtAsc(String postId, String tenantId);
}
