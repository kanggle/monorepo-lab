package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.access.ContentAccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContentAccessPolicyJpaRepository extends JpaRepository<ContentAccessPolicy, String> {

    Optional<ContentAccessPolicy> findByVisibilityKey(String visibilityKey);
}
