package com.example.auth.infrastructure.oauth2.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OAuthClientEntity}.
 *
 * <p>TASK-BE-252.
 */
public interface OAuthClientJpaRepository extends JpaRepository<OAuthClientEntity, String> {

    Optional<OAuthClientEntity> findByClientId(String clientId);
}
