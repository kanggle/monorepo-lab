package com.example.user.application.service;

import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Onboarding reaction to an IAM {@code account.created} event (ADR-MONO-037 P1).
 * Creates a minimal profile keyed on the {@code accountId} (= {@code profile.userId}).
 * Idempotent: re-delivery of the same account.created is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCreatedHandler {

    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void handle(UUID accountId) {
        if (userProfileRepository.existsByUserId(accountId)) {
            log.info("UserProfile already exists for accountId={}, skipping duplicate account.created", accountId);
            return;
        }

        UserProfile profile = UserProfile.createMinimal(accountId);
        userProfileRepository.save(profile);
        log.info("Created minimal UserProfile for accountId={} (PII sourced later from OIDC token / profile-update)", accountId);
    }
}
