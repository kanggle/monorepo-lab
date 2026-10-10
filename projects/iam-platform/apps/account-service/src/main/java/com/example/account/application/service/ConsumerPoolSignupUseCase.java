package com.example.account.application.service;

import com.example.account.application.command.ConsumerPoolSignupCommand;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.ConsumerPoolDisabledException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.SignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.PasswordPolicy;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-MONO-772 S3 (ADR-MONO-080 D6 · owner decision OD-3; auth-to-account.md § {@code POST
 * /internal/consumer-pool/signups}) — the site-less pool signup an operator invitee uses on the IdP
 * ({@code /operator-invitations/signup}).
 *
 * <p>The consumer signup's pool path ({@link SignupUseCase}) minus the two things that make it a SITE signup:
 * 🔴 <b>no {@code consumer_site_memberships} row and no {@code account.created} event</b> — an employee does not
 * become a store or fan member to accept a company invitation (772 AC-0 F8). When that person later visits a
 * consumer site, the first-visit consent there creates the membership and that site's {@code account.created}, as
 * for any pool account (multi-tenancy.md § 소비자 계정 풀 § 4 · § 6).
 *
 * <p>Everything else is the pool signup's: the pool duplicate, the § 2 coexistence refusal (an email that has a
 * consumer SITE account is refused with the existing duplicate answer — no new code, no new enumeration channel),
 * {@link PasswordPolicy}, the account + profile, the born-unified identity (fail-soft), and the auth-service
 * credential in {@code consumer-pool} — whose failure fails the whole signup (one transaction).
 *
 * <p>The verification mail is not sent here: the acceptance page answers {@code EMAIL_NOT_VERIFIED} with a link to
 * {@code /email-verification}, which sends it for the signed-in account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerPoolSignupUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AuthServicePort authServicePort;
    private final AccountIdentityProvisioner accountIdentityProvisioner;
    private final ConsumerAccountPool consumerAccountPool;
    private final ConsumerPoolFlag consumerPoolFlag;

    @Transactional
    public SignupResult execute(ConsumerPoolSignupCommand command) {
        // With the pool off there is no pool to put the account in (account-maintenance-internal.md's same code).
        if (!consumerPoolFlag.isEnabled()) {
            throw new ConsumerPoolDisabledException();
        }
        String normalizedEmail = command.email().trim().toLowerCase();

        // auth-to-account.md order: 1. a pool account with this email → «log in instead».
        if (accountRepository.existsByEmail(TenantId.CONSUMER_POOL, normalizedEmail)) {
            throw new AccountAlreadyExistsException(command.email());
        }
        // 2. § 2 coexistence — a consumer SITE account with this email → the consumer signup's answer, unchanged.
        consumerAccountPool.refuseIfEmailHasSiteAccount(normalizedEmail, command.email());

        PasswordPolicy.validate(command.password(), command.email());

        try {
            Account account = accountRepository.save(Account.create(TenantId.CONSUMER_POOL, command.email()));
            String identityId = mintAndAssignIdentity(account);
            Profile profile = Profile.create(
                    account.getId(), command.displayName(), command.locale(), command.timezone());
            profileRepository.save(profile);

            // 🔴 Deliberately absent: consumerAccountPool.joinOnSignup(...) and publishAccountCreated(...).

            try {
                authServicePort.createCredential(
                        account.getId(), account.getEmail(), command.password(),
                        TenantId.CONSUMER_POOL.value(), identityId);
            } catch (AuthServicePort.CredentialAlreadyExistsConflict e) {
                throw new AccountAlreadyExistsException(command.email());
            }
            log.info("site-less pool signup: account={} (no site membership, no account.created)", account.getId());
            return SignupResult.from(account);
        } catch (DataIntegrityViolationException e) {
            // A concurrent signup with the same email won the (tenant_id, email) unique key.
            throw new AccountAlreadyExistsException(command.email());
        }
    }

    /** {@link SignupUseCase}'s born-unified mint, fail-soft: a failure leaves the account unlinked, never blocks. */
    private String mintAndAssignIdentity(Account account) {
        String identityId;
        try {
            identityId = accountIdentityProvisioner.mintIdentity(TenantId.CONSUMER_POOL.value(), account.getEmail());
        } catch (RuntimeException e) {
            log.warn("born-unified identity mint failed (fail-soft, account {} born unlinked): {}",
                    account.getId(), e.getClass().getSimpleName());
            return null;
        }
        if (identityId != null) {
            accountRepository.assignIdentityId(TenantId.CONSUMER_POOL, account.getId(), identityId);
        }
        return identityId;
    }
}
