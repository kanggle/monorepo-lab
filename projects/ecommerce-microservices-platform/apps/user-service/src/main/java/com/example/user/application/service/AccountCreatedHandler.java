package com.example.user.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Onboarding reaction to an IAM {@code account.created} event (ADR-MONO-037 P1).
 * Creates a minimal profile keyed on the {@code accountId} (= {@code profile.userId}).
 * Idempotent: re-delivery of the same account.created is a no-op.
 *
 * <p>Since TASK-BE-575 the creation itself lives in {@link UserProfileProvisioner}, which
 * the request-time pull-through path shares — so a profile born from this event and one
 * born from a first request are identical, and whichever happens first makes the other a
 * no-op. This handler keeps only what is specific to the event: the payload carries no
 * raw email (it is PII-masked), so it provisions with none.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCreatedHandler {

    private final UserProfileProvisioner provisioner;

    public void handle(UUID accountId) {
        provisioner.ensureProvisioned(accountId, null);
    }
}
