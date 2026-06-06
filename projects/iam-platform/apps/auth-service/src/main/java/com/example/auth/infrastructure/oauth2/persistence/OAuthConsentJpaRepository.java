package com.example.auth.infrastructure.oauth2.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OAuthConsentEntity}.
 *
 * <p>TASK-BE-252.
 */
public interface OAuthConsentJpaRepository extends JpaRepository<OAuthConsentEntity, OAuthConsentId> {

    @Query("SELECT c FROM OAuthConsentEntity c WHERE c.clientId = :clientId " +
           "AND c.principalId = :principalId AND c.revokedAt IS NULL")
    Optional<OAuthConsentEntity> findActiveByClientIdAndPrincipalId(
            @Param("clientId") String clientId,
            @Param("principalId") String principalId);
}
