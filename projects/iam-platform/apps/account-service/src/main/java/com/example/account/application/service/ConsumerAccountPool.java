package com.example.account.application.service;

import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * TASK-BE-614 (ADR-MONO-078 A) — the account-service rules of the consumer-account pool
 * (multi-tenancy.md § 소비자 계정 풀 § 1·2·3), gathered in one place so the signup path and the
 * site-scoped lookups ask the same questions.
 *
 * <p>Everything here is behind {@link ConsumerPoolFlag}: with the flag off
 * {@link #signupGoesToPool(Tenant)} and {@link #lookupsIncludePoolMembers()} are {@code false} and
 * no caller reaches the other methods.
 */
@Component
@RequiredArgsConstructor
public class ConsumerAccountPool {

    private final ConsumerPoolFlag flag;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ConsumerSiteMembershipRepository membershipRepository;

    /**
     * § 2: a signup arriving from a consumer site (a {@code B2C_CONSUMER} tenant other than the
     * pool — {@link Tenant#isConsumerSite()}) creates a pool account. Console ({@code iam}) and
     * every non-consumer tenant keep the per-tenant signup (D1).
     */
    public boolean signupGoesToPool(Tenant signupTenant) {
        return flag.isEnabled() && signupTenant.isConsumerSite();
    }

    /** § 5: whether site-scoped lookups widen to that site's pool members. */
    public boolean lookupsIncludePoolMembers() {
        return flag.isEnabled();
    }

    /**
     * § 2 and § 3 (AC-6) — refuse a pool signup for an email that already has a <b>site account</b>
     * on any consumer site.
     *
     * <ul>
     *   <li>§ 2: accepting it would let two accounts share an email, and the form login can check
     *       only one password — someone else registering the email into the pool would lock the
     *       owner out of their own site account.</li>
     *   <li>§ 3 (AC-6, interim): an operator-faceted site account (an ecommerce seller, an
     *       ADR-MONO-044 D5 self-onboarded operator) is not moved into the pool until its own step
     *       ({@code TASK-MONO-745} / {@code TASK-MONO-746}). Those accounts ARE site accounts on a
     *       consumer site, so this one predicate refuses them too — no operator-specific lookup
     *       is needed in this step. Once {@code TASK-BE-618} moves the plain single-site accounts,
     *       the site accounts left behind are the operator-faceted ones and the two-site ones
     *       (linking, {@code TASK-MONO-743}, was closed unbuilt on 2026-10-06 — none exist), and this check is what keeps refusing their emails.</li>
     * </ul>
     *
     * <p>The refusal is the existing duplicate answer ({@link AccountAlreadyExistsException} →
     * {@code 409 ACCOUNT_ALREADY_EXISTS} → the signup page's "already registered" message) — not a
     * new response, so no new enumeration channel (§ 2 last sentence).
     *
     * <p>Every consumer site is asked through the tenant-scoped {@code existsByEmail}, so no
     * tenant-less lookup is introduced (§ 격리 회귀 방지). Suspended sites are included: an account
     * there still exists.
     */
    public void refuseIfEmailHasSiteAccount(String normalizedEmail, String emailAsTyped) {
        for (Tenant tenant : tenantRepository.findAllByTenantType(TenantType.B2C_CONSUMER)) {
            if (!tenant.isConsumerSite()) {
                continue;
            }
            if (accountRepository.existsByEmail(tenant.getTenantId(), normalizedEmail)) {
                throw new AccountAlreadyExistsException(emailAsTyped);
            }
        }
    }

    /**
     * TASK-BE-616 (§ 2, reverse direction) — refuse an INTERNAL account creation (operator provisioning,
     * bulk provisioning, product-service seller onboarding: {@code POST /internal/tenants/{t}/accounts})
     * into a consumer site when the email already has a <b>pool</b> account. § 2 forbids a pool account and
     * a site account sharing an email; {@link #refuseIfEmailHasSiteAccount} guards the «site account first,
     * pool signup second» order, this guards «pool first, site account second».
     *
     * <p>Same answer as any duplicate on that endpoint ({@link AccountAlreadyExistsException} →
     * {@code 409 ACCOUNT_ALREADY_EXISTS}). Interim consequence: a pool shopper cannot be onboarded as a
     * seller until {@code TASK-MONO-745} moves sellers into the pool — product-service lands such an
     * onboarding in its existing fail-soft {@code PENDING_PROVISIONING}.
     *
     * <p>Only consumer sites ({@link Tenant#isConsumerSite()}): B2B tenants (wms, erp, …) and customer
     * tenants keep per-tenant accounts that may share an email with anything (D1). Not behind the flag:
     * the coexistence is wrong whenever a pool account exists; with the flag off none are created
     * (one extra indexed read).
     */
    public void refuseIfEmailHasPoolAccount(Tenant creationTenant, String normalizedEmail, String emailAsTyped) {
        if (!creationTenant.isConsumerSite()) {
            return;
        }
        if (accountRepository.existsByEmail(TenantId.CONSUMER_POOL, normalizedEmail)) {
            throw new AccountAlreadyExistsException(emailAsTyped);
        }
    }

    /**
     * § 2: signing up on a site is consenting to it — record the membership in the same
     * transaction as the pool account. The {@code account.created} event of § 6 is published by
     * the caller with the site tenant.
     */
    public void joinOnSignup(Account poolAccount, TenantId siteTenantId) {
        if (!poolAccount.getTenantId().isConsumerPool()) {
            throw new IllegalStateException("only a pool account joins a consumer site: "
                    + poolAccount.getTenantId().value());
        }
        membershipRepository.insert(ConsumerSiteMembership.joinOnSignup(
                poolAccount.getId(), siteTenantId, poolAccount.getCreatedAt()));
    }
}
