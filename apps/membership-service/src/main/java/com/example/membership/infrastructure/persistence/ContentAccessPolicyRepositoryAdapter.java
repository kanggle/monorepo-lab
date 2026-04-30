package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.access.ContentAccessPolicy;
import com.example.membership.domain.access.ContentAccessPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ContentAccessPolicyRepositoryAdapter implements ContentAccessPolicyRepository {

    private final ContentAccessPolicyJpaRepository jpa;

    @Override
    public Optional<ContentAccessPolicy> findByVisibilityKey(String visibilityKey) {
        return jpa.findByVisibilityKey(visibilityKey);
    }
}
