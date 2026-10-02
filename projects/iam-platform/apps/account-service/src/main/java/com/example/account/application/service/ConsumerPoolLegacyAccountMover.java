package com.example.account.application.service;

import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.LegacyMoveOutcome;
import com.example.account.domain.consumerpool.LegacySiteAccount;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerPoolLegacyMoveRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-618 (multi-tenancy.md § 소비자 계정 풀 § 3; account-maintenance-internal.md) — moves ONE
 * single-site account into the consumer pool under the same id, in its OWN account_db transaction
 * ({@link Propagation#REQUIRES_NEW}): one account's failure never touches another's.
 *
 * <p>The order is binding (task «결정 — 순서와 멱등성»):
 * <ol>
 *   <li>lock the account row and re-check it is still a site account;</li>
 *   <li>re-check the account-side skip predicates under the lock;</li>
 *   <li>membership {@code (account, site, ACTIVE, consented_at = created_at)};</li>
 *   <li>copy {@code account_roles(site)} → {@code consumer_site_roles}, then delete them (composite FK —
 *       before the tenant changes);</li>
 *   <li>{@code tenant_id → consumer-pool} on {@code accounts} · {@code profiles} · the account's
 *       {@code identities} row ({@code account_status_history} is append-only and stays);</li>
 *   <li><b>last</b>, auth-service moves the credential. A refusal ({@link AuthServicePort.CredentialPoolMoveRefused})
 *       or failure is thrown out of this method, so the whole transaction rolls back — nothing moved.</li>
 * </ol>
 *
 * <p>No event is published: the account was already usable on that site ({@code account.created} went out
 * when it was created there — account-events.md).
 */
@Component
@RequiredArgsConstructor
public class ConsumerPoolLegacyAccountMover {

    /** The stored role that marks an ecommerce seller (ADR-MONO-042) — moved in TASK-MONO-745's step. */
    static final String SELLER_ROLE = "SELLER";

    private final ConsumerPoolLegacyMoveRepository moveRepository;
    private final AccountRepository accountRepository;
    private final AuthServicePort authServicePort;

    /**
     * @param site          the consumer site the candidate lived in when it was listed
     * @param accountId     the candidate
     * @param consumerSites every consumer site (for the two-site check)
     * @return {@link LegacyMoveOutcome#MOVED} or an account-side skip reason (nothing written)
     * @throws AuthServicePort.CredentialPoolMoveRefused auth-service refused — rolled back, the caller
     *                                                   records the reason as a skip
     * @throws RuntimeException                          anything else — rolled back, the caller records a failure
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LegacyMoveOutcome move(TenantId site, String accountId, List<TenantId> consumerSites) {
        Optional<LegacySiteAccount> locked = moveRepository.lockSiteAccount(site, accountId);
        if (locked.isEmpty()) {
            return LegacyMoveOutcome.NO_LONGER_CANDIDATE;
        }
        LegacySiteAccount account = locked.get();

        Optional<LegacyMoveOutcome> skip = accountSideSkip(account, consumerSites);
        if (skip.isPresent()) {
            return skip.get();
        }

        moveRepository.insertActiveMembershipConsentedAtCreation(site, accountId);
        moveRepository.copySiteRolesToConsumerSiteRoles(site, accountId);
        moveRepository.deleteSiteRoles(site, accountId);
        int moved = moveRepository.moveAccountRowsToPool(site, accountId, account.identityId());
        if (moved != 1) {
            // Impossible under the row lock; fail loudly rather than report a partial move.
            throw new IllegalStateException("account row of " + accountId + " did not move");
        }

        // LAST — a refusal or failure here rolls everything above back.
        authServicePort.moveCredentialToConsumerPool(accountId, site.value());
        return LegacyMoveOutcome.MOVED;
    }

    private Optional<LegacyMoveOutcome> accountSideSkip(LegacySiteAccount account, List<TenantId> consumerSites) {
        TenantId site = account.siteTenantId();
        if (moveRepository.findSiteRoleNames(site, account.accountId()).contains(SELLER_ROLE)) {
            return Optional.of(LegacyMoveOutcome.SELLER);
        }
        String email = account.email();
        for (TenantId other : consumerSites) {
            if (!other.equals(site) && accountRepository.existsByEmail(other, email)) {
                return Optional.of(LegacyMoveOutcome.TWO_SITE);
            }
        }
        if (accountRepository.existsByEmail(TenantId.CONSUMER_POOL, email)) {
            return Optional.of(LegacyMoveOutcome.POOL_EMAIL_EXISTS);
        }
        if (moveRepository.identityWouldConflict(site, account.identityId(), account.accountId())) {
            return Optional.of(LegacyMoveOutcome.IDENTITY_CONFLICT);
        }
        return Optional.empty();
    }
}
