package com.example.user.domain.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    UserProfile save(UserProfile userProfile);

    Optional<UserProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    PageResult<UserProfile> findByStatusAndEmailContaining(ProfileStatus status, String email, PageQuery pageQuery);

    PageResult<UserProfile> findByStatus(ProfileStatus status, PageQuery pageQuery);

    PageResult<UserProfile> findByEmailContaining(String email, PageQuery pageQuery);

    PageResult<UserProfile> findAll(PageQuery pageQuery);

    long countForTenant();

    long countForTenantCreatedBetween(Instant from, Instant to);
}
