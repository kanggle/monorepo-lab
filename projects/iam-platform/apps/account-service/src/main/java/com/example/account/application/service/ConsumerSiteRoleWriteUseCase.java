package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.EmailNotVerifiedException;
import com.example.account.application.exception.SiteMembershipRequiredException;
import com.example.account.application.exception.SiteRoleEmailMismatchException;
import com.example.account.application.exception.SiteRoleNotGrantableException;
import com.example.account.application.exception.SiteRoleRequiresPoolAccountException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.SiteRoleMutationResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.GrantableSiteRoles;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * TASK-MONO-752 (ADR-MONO-079 D5; contract {@code specs/contracts/http/internal/consumer-site-roles.md}) — the
 * first writer of {@code consumer_site_roles} over HTTP: grant and revoke one consumer site role of a pool
 * account. Caller: ecommerce product-service (seller members — accept writes {@code SELLER}, seller
 * suspension/closure removes it).
 *
 * <p><b>Grant rules, in order</b> (the first that fails answers; nothing is written by a refusal):
 * <ol>
 *   <li>the site tenant exists ({@link TenantNotFoundException});</li>
 *   <li>{@code (site, role)} is in the closed {@link GrantableSiteRoles} list and the tenant is a consumer
 *       site ({@link SiteRoleNotGrantableException});</li>
 *   <li>the account is a {@code consumer-pool} account — an account of the site itself is
 *       {@link SiteRoleRequiresPoolAccountException}, any other id {@link AccountNotFoundException};</li>
 *   <li>🔴 {@code expectedEmail} is the account's own email ({@link SiteRoleEmailMismatchException}). This is
 *       the check that keeps «whoever holds the invitation link» from becoming a seller (ticket Failure
 *       Scenario 1): the caller knows which address it invited, only this service knows which address the
 *       logged-in account has;</li>
 *   <li>🔴 TASK-MONO-770 (ADR-MONO-080 D3 · rider R1, amends ADR-MONO-079 D5) — the account's email is
 *       <b>verified</b> ({@link VerifiedEmailRequirement} → {@code EmailNotVerifiedException}). The rule above
 *       proves only that the account <i>names</i> the invited address; IAM never checked the person owns it, so
 *       whoever signed up to the pool with someone else's address could accept that person's invitation. Checked
 *       before the idempotency short-cut below (a second seller's invitation attaches a new company role even
 *       when the site role is already held); it never revokes a role already held (ADR-MONO-080 D5);</li>
 *   <li>the account is an ACTIVE member of the site ({@link SiteMembershipRequiredException}) — a grant never
 *       creates a membership (membership = consent, ADR-MONO-078 D3).</li>
 * </ol>
 *
 * <p><b>Revoke</b> runs rules 1–3 and removes the row. 🔴 It never locks the account and never touches the
 * membership: the person keeps shopping ({@code CUSTOMER} is the seed) and keeps the fan site (ADR-MONO-079 D5).
 *
 * <p>Both are idempotent ({@code changed=false} on a no-op, no audit row). A real change writes one
 * {@code account_status_history} audit row under the SITE tenant; no outbox event — {@code account.roles.changed}
 * describes {@code account_roles} of the account's own tenant, and issuance reads this table directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerSiteRoleWriteUseCase {

    static final String ACTOR_TYPE = "provisioning_system";

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ConsumerSiteMembershipRepository membershipRepository;
    private final AccountStatusHistoryRepository historyRepository;

    @Transactional
    public SiteRoleMutationResult grant(String siteTenantId, String accountId, String roleName,
                                        String expectedEmail, String operatorId) {
        TenantId site = new TenantId(siteTenantId);
        requireGrantable(site, roleName);
        Account account = requirePoolAccount(site, accountId);

        if (!sameEmail(expectedEmail, account.getEmail())) {
            log.info("site-role grant refused: email mismatch (site={}, account={}, role={})",
                    site.value(), accountId, roleName);
            throw new SiteRoleEmailMismatchException(
                    "The account is not the one the email names; nothing was granted");
        }
        try {
            VerifiedEmailRequirement.require(account);
        } catch (EmailNotVerifiedException refused) {
            log.info("site-role grant refused: email not verified (site={}, account={}, role={})",
                    site.value(), accountId, roleName);
            throw refused;
        }
        Optional<ConsumerSiteMembership> membership = membershipRepository.find(site, accountId);
        if (membership.filter(ConsumerSiteMembership::isActive).isEmpty()) {
            throw new SiteMembershipRequiredException(
                    "The account has no ACTIVE membership of site " + site.value() + "; nothing was granted");
        }

        List<String> before = membershipRepository.findSiteRoles(site, accountId);
        if (before.contains(roleName)) {
            return new SiteRoleMutationResult(accountId, siteTenantId, before, false);
        }
        String grantedBy = operatorId != null && !operatorId.isBlank() ? operatorId : siteTenantId;
        membershipRepository.addSiteRole(site, accountId, roleName, grantedBy, Instant.now());
        audit(account, site, "SITE_ROLE_GRANT", roleName, grantedBy);
        log.info("site-role granted: site={} account={} role={}", site.value(), accountId, roleName);
        return new SiteRoleMutationResult(accountId, siteTenantId,
                membershipRepository.findSiteRoles(site, accountId), true);
    }

    @Transactional
    public SiteRoleMutationResult revoke(String siteTenantId, String accountId, String roleName, String operatorId) {
        TenantId site = new TenantId(siteTenantId);
        requireGrantable(site, roleName);
        Account account = requirePoolAccount(site, accountId);

        boolean removed = membershipRepository.removeSiteRole(site, accountId, roleName);
        if (removed) {
            String actor = operatorId != null && !operatorId.isBlank() ? operatorId : siteTenantId;
            audit(account, site, "SITE_ROLE_REVOKE", roleName, actor);
            log.info("site-role revoked: site={} account={} role={}", site.value(), accountId, roleName);
        }
        return new SiteRoleMutationResult(accountId, siteTenantId,
                membershipRepository.findSiteRoles(site, accountId), removed);
    }

    private void requireGrantable(TenantId site, String roleName) {
        Tenant tenant = tenantRepository.findById(site)
                .orElseThrow(() -> new TenantNotFoundException(site.value()));
        if (!tenant.isConsumerSite() || !GrantableSiteRoles.isGrantable(site, roleName)) {
            throw new SiteRoleNotGrantableException(
                    "Role " + roleName + " cannot be granted on site " + site.value());
        }
    }

    private Account requirePoolAccount(TenantId site, String accountId) {
        Optional<Account> pool = accountRepository.findById(TenantId.CONSUMER_POOL, accountId);
        if (pool.isPresent()) {
            return pool.get();
        }
        if (accountRepository.findById(site, accountId).isPresent()) {
            throw new SiteRoleRequiresPoolAccountException(
                    "The account belongs to site " + site.value() + ", not to the consumer pool");
        }
        throw new AccountNotFoundException(accountId);
    }

    static boolean sameEmail(String expected, String actual) {
        if (expected == null || actual == null || expected.isBlank()) {
            return false;
        }
        return expected.trim().toLowerCase(Locale.ROOT).equals(actual.trim().toLowerCase(Locale.ROOT));
    }

    private void audit(Account account, TenantId site, String action, String roleName, String actorId) {
        String escapedRole = roleName.replace("\"", "\\\"");
        historyRepository.save(AccountStatusHistoryEntry.create(
                site.value(),
                account.getId(),
                account.getStatus(),
                account.getStatus(),
                StatusChangeReason.OPERATOR_PROVISIONING_ROLES_REPLACE,
                ACTOR_TYPE,
                actorId,
                "{\"action\":\"" + action + "\",\"site\":\"" + site.value() + "\",\"role\":\"" + escapedRole + "\"}"));
    }
}
