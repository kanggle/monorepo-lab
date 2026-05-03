package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.follow.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowJpaRepository extends JpaRepository<Follow, Follow.FollowId> {

    Optional<Follow> findByFanAccountIdAndArtistAccountIdAndTenantId(
            String fanAccountId, String artistAccountId, String tenantId);

    boolean existsByFanAccountIdAndArtistAccountIdAndTenantId(
            String fanAccountId, String artistAccountId, String tenantId);
}
