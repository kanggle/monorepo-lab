package com.example.account.application.service;

import com.example.account.application.result.ConsumerSiteMembershipResult;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-615 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 4) — the read auth-service makes
 * when a pool principal asks for a consumer site's token, at form login, at {@code /oauth2/authorize}
 * and at every issuance (authorization_code and refresh_token).
 *
 * <p><b>Always an answer, never a 404.</b> "Not a consumer site", "not a pool account" and "no
 * membership" are all normal answers carried in the body, so that the only 404 auth-service can see
 * is "this endpoint does not exist" (an older account-service) — which it treats as a failed lookup
 * and fails CLOSED on (no token for a pool principal).
 *
 * <p><b>Not behind {@code iam.consumer-pool.enabled}.</b> The flag decides whether new signups land
 * in the pool (TASK-BE-614); a pool account that already exists must keep signing in whatever the
 * flag says. With the flag off nothing creates pool accounts, so nothing reaches this read.
 *
 * <p>Read-only: no audit row, no outbox event, no mutation. The account is looked up only in the
 * pool tenant ({@link AccountRepository#findById} with {@link TenantId#CONSUMER_POOL}) — no
 * tenant-less lookup is introduced (multi-tenancy.md § 격리 회귀 방지).
 */
@Service
@RequiredArgsConstructor
public class GetConsumerSiteMembershipUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ConsumerSiteMembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public ConsumerSiteMembershipResult execute(String siteTenantId, String accountId) {
        TenantId site = new TenantId(siteTenantId);
        Optional<Tenant> tenant = tenantRepository.findById(site);
        boolean consumerSite = tenant.map(Tenant::isConsumerSite).orElse(false);
        String siteTenantType = tenant.map(t -> t.getTenantType().name()).orElse(null);

        if (!consumerSite || accountRepository.findById(TenantId.CONSUMER_POOL, accountId).isEmpty()) {
            return new ConsumerSiteMembershipResult(
                    accountId, siteTenantId, consumerSite, siteTenantType, null, List.of());
        }

        Optional<ConsumerSiteMembership> membership = membershipRepository.find(site, accountId);
        String status = membership.map(m -> m.getStatus().name()).orElse(null);
        List<String> siteRoles = membership.filter(ConsumerSiteMembership::isActive)
                .map(m -> membershipRepository.findSiteRoles(site, accountId))
                .orElse(List.of());
        // TASK-BE-619 — who left: auth-service shows the consent screen again only for SELF.
        String leftBy = membership.filter(m -> !m.isActive())
                .map(ConsumerSiteMembership::getLeftBy)
                .map(Enum::name)
                .orElse(null);
        return new ConsumerSiteMembershipResult(
                accountId, siteTenantId, true, siteTenantType, status, siteRoles, leftBy);
    }
}
