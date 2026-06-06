package com.example.membership.domain.access;

import java.util.Optional;

public interface ContentAccessPolicyRepository {

    Optional<ContentAccessPolicy> findByVisibilityKey(String visibilityKey);
}
