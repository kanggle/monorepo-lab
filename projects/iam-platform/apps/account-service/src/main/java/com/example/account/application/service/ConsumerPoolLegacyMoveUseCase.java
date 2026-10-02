package com.example.account.application.service;

import com.example.account.application.exception.ConsumerPoolDisabledException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.ConsumerPoolLegacyMoveResult;
import com.example.account.application.result.LegacyMoveOutcome;
import com.example.account.domain.consumerpool.LegacyMoveCandidate;
import com.example.account.domain.repository.ConsumerPoolLegacyMoveRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-618 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 3; account-maintenance-internal.md) —
 * the batch behind {@code POST /internal/consumer-pool/legacy-moves}: move every single-site consumer
 * account it can into the pool under the same id, one account per transaction
 * ({@link ConsumerPoolLegacyAccountMover}).
 *
 * <p><b>Not transactional itself</b> — each account commits or rolls back on its own, so one failure (an
 * auth-service outage, a constraint, a refusal) never undoes or blocks another account's move.
 *
 * <p><b>Re-runnable.</b> Internal provisioning keeps creating site accounts until ADR-MONO-080, so this is run
 * again and again: a moved account is no longer a candidate, a skipped one is looked at again (its reason may
 * be gone), a failed one is retried. The cursor ({@code afterAccountId}) lets a caller walk past the
 * permanently skipped head of the candidate list instead of re-reading it forever.
 *
 * <p>Refused as a whole when {@code iam.consumer-pool.enabled} is off ({@link ConsumerPoolDisabledException}):
 * with the flag off the site-scoped lookups (§ 5) stop including pool members, so a moved account would
 * vanish from its site.
 *
 * <p>Audit: one structured log line per account (id · site · outcome) — never the email. This is a machine-run
 * batch without an operator context (the admin-service backfill precedent: no {@code admin_actions} row).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerPoolLegacyMoveUseCase {

    public static final int DEFAULT_LIMIT = 500;
    public static final int MAX_LIMIT = 1000;

    private final ConsumerPoolFlag consumerPoolFlag;
    private final TenantRepository tenantRepository;
    private final ConsumerPoolLegacyMoveRepository moveRepository;
    private final ConsumerPoolLegacyAccountMover mover;

    /**
     * @param limit          candidates to look at, {@code null} → {@link #DEFAULT_LIMIT}; must be 1..{@link #MAX_LIMIT}
     * @param afterAccountId cursor — only ids greater than this; {@code null} → from the start
     */
    public ConsumerPoolLegacyMoveResult execute(Integer limit, String afterAccountId) {
        if (!consumerPoolFlag.isEnabled()) {
            throw new ConsumerPoolDisabledException();
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        List<TenantId> consumerSites = tenantRepository.findAllByTenantType(TenantType.B2C_CONSUMER).stream()
                .filter(Tenant::isConsumerSite)
                .map(Tenant::getTenantId)
                .toList();
        List<LegacyMoveCandidate> candidates =
                moveRepository.findCandidates(consumerSites, afterAccountId, effectiveLimit);

        Map<String, Integer> skipped = new LinkedHashMap<>();
        for (LegacyMoveOutcome outcome : LegacyMoveOutcome.values()) {
            if (outcome.isSkip()) {
                skipped.put(outcome.name(), 0);
            }
        }
        int moved = 0;
        int failed = 0;
        List<String> movedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        for (LegacyMoveCandidate candidate : candidates) {
            String accountId = candidate.accountId();
            String site = candidate.siteTenantId().value();
            LegacyMoveOutcome outcome;
            try {
                outcome = mover.move(candidate.siteTenantId(), accountId, consumerSites);
            } catch (AuthServicePort.CredentialPoolMoveRefused refused) {
                outcome = LegacyMoveOutcome.valueOf(refused.reason());
            } catch (RuntimeException e) {
                failed++;
                addCapped(failedIds, accountId);
                log.warn("consumer-pool legacy move: account={} site={} outcome=FAILED cause={}",
                        accountId, site, e.getClass().getSimpleName());
                continue;
            }
            if (outcome == LegacyMoveOutcome.MOVED) {
                moved++;
                addCapped(movedIds, accountId);
            } else {
                skipped.merge(outcome.name(), 1, Integer::sum);
            }
            log.info("consumer-pool legacy move: account={} site={} outcome={}", accountId, site, outcome);
        }

        String next = candidates.size() == effectiveLimit && !candidates.isEmpty()
                ? candidates.get(candidates.size() - 1).accountId()
                : null;
        log.info("consumer-pool legacy move run: scanned={} moved={} failed={} skipped={} next={}",
                candidates.size(), moved, failed, skipped, next);
        return new ConsumerPoolLegacyMoveResult(candidates.size(), moved, skipped, failed,
                List.copyOf(movedIds), List.copyOf(failedIds), next);
    }

    private static void addCapped(List<String> ids, String id) {
        if (ids.size() < ConsumerPoolLegacyMoveResult.ID_LIST_CAP) {
            ids.add(id);
        }
    }
}
