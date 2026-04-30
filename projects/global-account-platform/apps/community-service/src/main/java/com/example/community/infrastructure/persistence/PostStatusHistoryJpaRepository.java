package com.example.community.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostStatusHistoryJpaRepository extends JpaRepository<PostStatusHistoryJpaEntity, Long> {
    List<PostStatusHistoryJpaEntity> findByPostIdOrderByOccurredAtAsc(String postId);
}
