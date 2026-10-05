package com.example.account.application.service;

import com.example.account.application.command.SocialSignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountEventPublisher eventPublisher;
    private final AccountIdentityProvisioner accountIdentityProvisioner;
    private final ActiveTenantGuard activeTenantGuard;
    /** TASK-BE-620 (multi-tenancy.md § 소비자 계정 풀 § 2): the no-coexistence check against the pool. */
    private final ConsumerAccountPool consumerAccountPool;

    @Transactional
    public SocialSignupResult execute(SocialSignupCommand command) {
        // TASK-BE-507: the caller (auth-service) resolves the initiating OIDC client's tenant
        // and already stamps it on the social-identity row and the token — it now sends it here
        // too, so the account row no longer contradicts the token. Header-less → fan-platform.
        TenantId tenantId = TenantId.fromHeaderOrDefault(command.tenantId());
        Tenant tenant = activeTenantGuard.requireActive(tenantId);

        String normalizedEmail = command.email().trim().toLowerCase();

        // Check if account with this email already exists within this tenant. Kept first and unchanged by
        // TASK-BE-617: a SITE account (one made before ADR-MONO-078 and not moved into the pool) keeps
        // working as it always did until linked (multi-tenancy.md § 소비자 계정 풀 — «묶이기 전까지 그대로»).
        Optional<Account> existing = accountRepository.findByEmail(tenantId, normalizedEmail);
        if (existing.isPresent()) {
            return SocialSignupResult.fromExisting(existing.get());
        }

        // TASK-BE-617 (ADR-MONO-078 D4; multi-tenancy.md § 소비자 계정 풀 § 2): with the pool flag on, a
        // consumer site's NEW social signup is a pool account — the same predicate the form signup asks.
        if (consumerAccountPool.signupGoesToPool(tenant)) {
            return signUpIntoPool(command, tenant, normalizedEmail);
        }

        // TASK-BE-620 — the per-tenant path (flag off, or a non-consumer tenant): an email that already
        // has a POOL account is refused rather than given a second, site account: § 2 forbids the two
        // coexisting (the form login could then check only one of them). The same-site link above stays
        // first and unchanged. Same answer as any duplicate (409 ACCOUNT_ALREADY_EXISTS); consumer sites
        // only, B2B untouched. TASK-BE-617 keeps this check (the pool path asks it too — there it is the
        // D2 control): see the task file's «620 결정».
        consumerAccountPool.refuseIfEmailHasPoolAccount(tenant, normalizedEmail, command.email());

        try {
            // Create new account (no password for social-only accounts)
            Account account = Account.create(tenantId, command.email());
            account = accountRepository.save(account);

            // TASK-BE-381 (ADR-036 M1): born-unified — assign the central identity at creation.
            assignBornUnifiedIdentity(tenantId, account);

            // Create profile with displayName from provider
            Profile profile = Profile.create(
                    account.getId(),
                    command.displayName(),
                    null,  // locale: use default
                    null   // timezone: use default
            );
            profileRepository.save(profile);

            // Publish account.created outbox event
            eventPublisher.publishAccountCreated(account, account.getTenantId().value(), profile.getLocale());

            return SocialSignupResult.fromNew(account);
        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent social signup with same email
            // Re-fetch and return existing account
            Account racedAccount = accountRepository.findByEmail(tenantId, normalizedEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "DataIntegrityViolation but account not found for email"));
            return SocialSignupResult.fromExisting(racedAccount);
        }
    }

    /**
     * TASK-BE-617 — a consumer site's new social signup, born in the pool (multi-tenancy.md § 소비자 계정
     * 풀 § 2 · § 6; auth-to-account-social.md 3.1–3.3). Mirrors {@link SignupUseCase}'s pool branch, minus
     * the credential (a social-only account has no password).
     *
     * <ol>
     *   <li>🔴 An email that already has a <b>pool</b> account is refused — never linked (ADR-MONO-078 D2).
     *       The legacy path above links by email inside the tenant; carried over to the pool, that would let
     *       anyone whose provider asserts the victim's address into the victim's pool account without its
     *       password: IAM does not require a verified email, and not every provider verifies one.
     *       auth-service has already looked the provider identity up in the pool and found none, so
     *       «the pool account exists» here means «it does not hold this provider identity».</li>
     *   <li>An email with a <b>site</b> account on another consumer site is refused — the same § 2
     *       no-coexistence the form signup enforces ({@link ConsumerAccountPool#refuseIfEmailHasSiteAccount}).</li>
     *   <li>Otherwise: the pool account, its profile, the site membership (signing up = consenting) and
     *       {@code account.created} carrying the SITE, once.</li>
     * </ol>
     *
     * <p>A unique-key race is refused too ({@link AccountAlreadyExistsException}) — the legacy path's
     * «re-read and return the raced account» would be exactly the email link step 1 forbids.
     */
    private SocialSignupResult signUpIntoPool(SocialSignupCommand command, Tenant site,
                                              String normalizedEmail) {
        TenantId siteTenantId = site.getTenantId();
        // § 2 / D2 — the pool-duplicate check first: it is the AC-2 control (the same TASK-BE-620
        // predicate the per-tenant path asks), and the site loop below would otherwise answer the same
        // 409 for a different reason.
        consumerAccountPool.refuseIfEmailHasPoolAccount(site, normalizedEmail, command.email());
        consumerAccountPool.refuseIfEmailHasSiteAccount(normalizedEmail, command.email());

        try {
            Account account = accountRepository.save(Account.create(TenantId.CONSUMER_POOL, command.email()));
            assignBornUnifiedIdentity(TenantId.CONSUMER_POOL, account);

            Profile profile = Profile.create(account.getId(), command.displayName(), null, null);
            profileRepository.save(profile);

            // § 2: signing up on the site is consenting to it — the membership in the same tx.
            consumerAccountPool.joinOnSignup(account, siteTenantId);

            // § 6: the SITE, never consumer-pool (the ecommerce consumer builds its profile under it).
            eventPublisher.publishAccountCreated(account, siteTenantId.value(), profile.getLocale());

            return SocialSignupResult.fromNew(account);
        } catch (DataIntegrityViolationException e) {
            throw new AccountAlreadyExistsException(command.email());
        }
    }

    /**
     * TASK-BE-381 (ADR-MONO-036 P1/P2, M1): mint the account's central identity at
     * creation (born-unified), fail-soft. The mint runs in a REQUIRES_NEW transaction
     * ({@link AccountIdentityProvisioner}) so a failure cannot poison this registration
     * transaction; on any failure the account is born unlinked (identity_id stays NULL,
     * reconciled later) — registration never blocks on the identity infrastructure
     * (the ADR-034 availability stance). The {@code reuseExisting} mint converges the
     * consumer and operator sides on the SAME identity (ADR-036 P1).
     */
    private void assignBornUnifiedIdentity(TenantId tenantId, Account account) {
        String identityId;
        try {
            identityId = accountIdentityProvisioner.mintIdentity(tenantId.value(), account.getEmail());
        } catch (RuntimeException e) {
            log.warn("born-unified identity mint failed (fail-soft, account {} born unlinked) tenant={}: {}",
                    account.getId(), tenantId.value(), e.toString());
            return;
        }
        if (identityId != null) {
            accountRepository.assignIdentityId(tenantId, account.getId(), identityId);
        }
    }
}
