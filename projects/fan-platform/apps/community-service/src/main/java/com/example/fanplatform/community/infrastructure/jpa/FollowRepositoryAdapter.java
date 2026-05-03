package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.follow.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FollowRepositoryAdapter implements FollowRepository {

    private final FollowJpaRepository jpa;

    @Override
    public Follow save(Follow follow) {
        return jpa.save(follow);
    }

    @Override
    public void delete(Follow follow) {
        jpa.delete(follow);
    }

    @Override
    public Optional<Follow> find(String fanAccountId, String artistAccountId, String tenantId) {
        return jpa.findByFanAccountIdAndArtistAccountIdAndTenantId(
                fanAccountId, artistAccountId, tenantId);
    }

    @Override
    public boolean exists(String fanAccountId, String artistAccountId, String tenantId) {
        return jpa.existsByFanAccountIdAndArtistAccountIdAndTenantId(
                fanAccountId, artistAccountId, tenantId);
    }
}
