package com.example.account.infrastructure.anonymizer;

import com.example.account.application.util.DigestUtils;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * PII Anonymizer (R7 — Right to Erasure, retention.md §2).
 *
 * Performs deferred PII anonymization for the grace-period batch path
 * ({@link com.example.account.infrastructure.scheduler.AccountAnonymizationScheduler}).
 * The GDPR/PIPA immediate-erasure path uses
 * {@link com.example.account.application.service.GdprDeleteUseCase} directly.
 *
 * <p>Both paths converge on the same masking primitives:
 * <ul>
 *   <li>{@link Account#maskEmail(String, String)} — replaces the email and stamps {@code email_hash}.</li>
 *   <li>{@link Profile#maskPii()} — nulls out display_name / phone_number / birth_date and stamps {@code masked_at}.</li>
 * </ul>
 *
 * <p>Per spec retention.md §2.5 / §2.6 the {@code accounts.email} is rewritten to
 * {@code anon_{SHA-256(original)[:12]}@deleted.local} for the deferred batch path,
 * and {@code email_hash} (full 64-char hex) is preserved for re-signup blocking.
 */
@Component
@RequiredArgsConstructor
public class PiiAnonymizer {

    private static final String ANON_EMAIL_DOMAIN = "@deleted.local";
    private static final String ANON_EMAIL_PREFIX = "anon_";
    private static final int ANON_EMAIL_HASH_LENGTH = 12;

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    /**
     * Anonymize PII for the given account: rewrite email + email_hash on the account row,
     * null out PII fields and stamp {@code masked_at} on the profile (if present).
     *
     * <p>Caller is responsible for surrounding this in a transaction
     * (per-account boundary — see retention.md §2.9).
     *
     * @param account the account to anonymize (must be in DELETED state and beyond grace period)
     */
    public void anonymize(Account account) {
        String originalEmail = account.getEmail();
        String fullHash = DigestUtils.sha256Hex(originalEmail);
        String maskedEmail = ANON_EMAIL_PREFIX + fullHash.substring(0, ANON_EMAIL_HASH_LENGTH) + ANON_EMAIL_DOMAIN;

        account.maskEmail(fullHash, maskedEmail);
        accountRepository.save(account);

        Optional<Profile> profileOpt = profileRepository.findByAccountId(account.getId());
        profileOpt.ifPresent(profile -> {
            profile.maskPii();
            profileRepository.save(profile);
        });
    }

}
