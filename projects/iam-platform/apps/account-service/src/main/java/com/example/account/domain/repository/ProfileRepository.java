package com.example.account.domain.repository;

import com.example.account.domain.profile.Profile;
import com.example.account.domain.tenant.TenantId;

import java.util.Optional;

public interface ProfileRepository {

    Profile save(Profile profile);

    /**
     * TASK-BE-231: Save a profile with an explicit tenantId.
     * Used by the provisioning flow where the tenant differs from the default fan-platform.
     */
    Profile save(Profile profile, TenantId tenantId);

    Optional<Profile> findByAccountId(String accountId);
}
