package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.result.ConsumerSiteMembershipResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * TASK-BE-616 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 4 · § 6) — the write behind the
 * first-visit consent screen: a pool account that accepted a consumer site's consent becomes an ACTIVE
 * member of that site, and {@code account.created} is published <b>once</b> with that site as its
 * {@code tenantId} (account-events.md § account.created, «다른 사이트 첫 방문 동의» row).
 *
 * <p><b>Idempotent on {@code (accountId, site)}.</b> The write happens only when there is no
 * membership row yet. A row that already exists — ACTIVE (a double submit, a back-button replay) or
 * LEFT — is left exactly as it is and no event is published: the event means «this account became
 * usable on this site», which happens once. A LEFT membership is reopened by consent <b>only when the
 * person left it themself</b> (TASK-BE-619, owner decision 2026-10-03 «다시 동의하면 복귀»): it becomes
 * ACTIVE again with a new {@code consented_at}, and still no event. One the site's operator removed
 * ({@code left_by = OPERATOR}) stays LEFT — consent cannot undo a removal. Two concurrent first
 * consents race on the primary key; the loser's transaction rolls back with its event, and the caller
 * answers with the read ({@link GetConsumerSiteMembershipUseCase}) — see the controller.
 *
 * <p><b>Always an answer.</b> Like the read, every "no" is carried in the returned result rather than
 * an exception: not a consumer site, a suspended site, not a pool account → nothing is written and
 * the answer shows no ACTIVE membership, which auth-service treats as «no token». Only the site role
 * <i>seed</i> ({@code CUSTOMER}/{@code FAN}) follows from the membership — it is computed at issuance
 * and never stored, so no role is written before (or by) consent (task Failure Scenario 1).
 *
 * <p>Not behind {@code iam.consumer-pool.enabled}, like the read: a pool account that already exists
 * must be able to enter another site whatever the flag says.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentToConsumerSiteUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final ConsumerSiteMembershipRepository membershipRepository;
    private final AccountEventPublisher eventPublisher;
    private final GetConsumerSiteMembershipUseCase getConsumerSiteMembershipUseCase;

    @Transactional
    public ConsumerSiteMembershipResult execute(String siteTenantId, String accountId) {
        TenantId site = new TenantId(siteTenantId);
        Optional<Tenant> tenant = tenantRepository.findById(site);
        boolean consumerSite = tenant.map(Tenant::isConsumerSite).orElse(false);
        boolean siteOpen = tenant.map(Tenant::isActive).orElse(false);
        Optional<Account> poolAccount = consumerSite
                ? accountRepository.findById(TenantId.CONSUMER_POOL, accountId)
                : Optional.empty();

        Optional<ConsumerSiteMembership> existing = consumerSite && siteOpen && poolAccount.isPresent()
                ? membershipRepository.find(site, accountId)
                : Optional.empty();
        if (consumerSite && siteOpen && poolAccount.isPresent() && existing.isEmpty()) {
            membershipRepository.insert(ConsumerSiteMembership.joinOnConsent(accountId, site, Instant.now()));
            String locale = profileRepository.findByAccountId(accountId).map(Profile::getLocale).orElse(null);
            // § 6: the site, never consumer-pool — the ecommerce consumer builds its profile under it.
            eventPublisher.publishAccountCreated(poolAccount.get(), site.value(), locale);
            log.info("consumer-site consent: account {} joined site {} (account.created published)",
                    accountId, site.value());
        } else if (existing.isPresent() && existing.get().isReopenableByConsent()) {
            // TASK-BE-619 (owner decision 2026-10-03 «다시 동의하면 복귀») — the person left this site
            // themself and consented again: ACTIVE again. NO account.created — the event means «usable on
            // this site for the first time» and that already happened once (§ 6, one per (account, site)).
            membershipRepository.update(existing.get().rejoinOnConsent(Instant.now()));
            log.info("consumer-site consent: account {} rejoined site {} after leaving it themself",
                    accountId, site.value());
        } else if (consumerSite && !siteOpen) {
            log.info("consumer-site consent refused: site {} is not ACTIVE (account {})", site.value(), accountId);
        }
        return getConsumerSiteMembershipUseCase.execute(siteTenantId, accountId);
    }
}
