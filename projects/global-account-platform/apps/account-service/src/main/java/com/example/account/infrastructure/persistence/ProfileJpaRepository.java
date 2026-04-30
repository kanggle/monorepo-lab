package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileJpaRepository extends JpaRepository<ProfileJpaEntity, Long> {

    Optional<ProfileJpaEntity> findByAccountId(String accountId);
}
