package com.example.auth.infrastructure.oauth2.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link OAuthScopeEntity}.
 *
 * <p>TASK-BE-252.
 */
public interface OAuthScopeJpaRepository extends JpaRepository<OAuthScopeEntity, Long> {

    List<OAuthScopeEntity> findBySystemTrue();
}
