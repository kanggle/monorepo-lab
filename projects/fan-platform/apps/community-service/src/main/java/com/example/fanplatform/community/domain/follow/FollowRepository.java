package com.example.fanplatform.community.domain.follow;

import java.util.Optional;

public interface FollowRepository {

    Follow save(Follow follow);

    void delete(Follow follow);

    Optional<Follow> find(String fanAccountId, String artistAccountId, String tenantId);

    boolean exists(String fanAccountId, String artistAccountId, String tenantId);
}
