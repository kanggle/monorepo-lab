import { getServerEnv } from '@/shared/config/env';
import { logger } from '@/shared/lib/logger';
import {
  HEADLINE_PERIOD,
  headlineFromMetricsBody,
  type Headline,
  type HeadlineDomain,
} from '@/shared/lib/operator-overview-headline';
import {
  CARD_ORDER,
  compose,
  type CompositionEnvelope,
  type FetchLeg,
  type LegSpec,
  type OverviewCredentials,
} from './console-composition';

/**
 * Card headline metrics for the operator overview (ADR-MONO-084 D7,
 * TASK-PC-FE-368; contract § 2.4.9.1 «Card headline metric»). SERVER-ONLY.
 *
 * 🔴 A SEPARATE `compose()` PASS, not a second call inside a card's leg. The
 * overview keeps «one call per card» (owner, TASK-PC-FE-296): a second call
 * inside a leg would share that leg's one `LEG_TIMEOUT_MS` and could cost the
 * card its only number. Here each metrics read is its OWN leg with its OWN
 * timeout, run in parallel with the card legs (latency = max, not sum), and its
 * outcome is a field of the card (`headline`), never the card's status. So a
 * metrics read that is slow, 5xx, 403 or unparseable changes that card's
 * `headline` and nothing else.
 *
 * The addresses and credentials are the ones each domain's own «실적» strip uses
 * (`features/{…}/api/*-metrics-api.ts`) — those modules sit in `features/` and
 * `shared/` may not import them (architecture.md § Forbidden Dependencies), so
 * the paths are restated here and pinned against them by a unit test. The parse
 * is NOT restated: `shared/api/performance-metrics.ts` is the one schema.
 *
 * `period=week` — «이번 주 값 · 직전 대비 증감» (ADR-084 D7): the response's
 * `current` vs `previous` for the same-length previous window.
 */

const P = `period=${HEADLINE_PERIOD}`;

/** The six metrics legs — same bases and credentials as the card legs (§ 2.4.9.1 producer table). */
export async function operatorOverviewHeadlineLegs(c: OverviewCredentials): Promise<LegSpec[]> {
  const env = getServerEnv();
  const domainHeaders = {
    Accept: 'application/json',
    Authorization: `Bearer ${c.domainFacingToken}`,
    'X-Request-Id': c.requestId,
  };
  const leg = (domain: HeadlineDomain, url: string, headers: Record<string, string>): LegSpec => ({
    domain,
    kind: 'data',
    url,
    headers,
  });
  return [
    // IAM scopes this read by the `tenantId` QUERY param (like the accounts leg)
    // and by the exchanged operator token — never the IAM OIDC token.
    leg(
      'iam',
      `${env.IAM_ADMIN_API_BASE}/api/admin/metrics?${P}&tenantId=${encodeURIComponent(c.tenant)}`,
      {
        Accept: 'application/json',
        Authorization: `Bearer ${c.operatorToken}`,
        'X-Tenant-Id': c.tenant,
        'X-Request-Id': c.requestId,
      },
    ),
    leg('wms', `${env.WMS_ADMIN_BASE_URL}/dashboard/metrics?${P}`, domainHeaders),
    leg('scm', `${env.SCM_GATEWAY_BASE_URL}/api/v1/procurement/po/metrics?${P}`, domainHeaders),
    // The account service (`FINANCE_BASE_URL`) — NOT the ledger base the card leg uses.
    leg('finance', `${env.FINANCE_BASE_URL}/api/finance/accounts/metrics?${P}`, domainHeaders),
    leg('erp', `${env.ERP_BASE_URL}/api/erp/approval/metrics?${P}`, domainHeaders),
    leg('ecommerce', `${env.ECOMMERCE_ADMIN_BASE_URL}/orders/metrics?${P}`, domainHeaders),
  ];
}

export type HeadlineResult =
  | { unauthorized: true }
  | { unauthorized: false; headlines: ReadonlyMap<HeadlineDomain, Headline> };

const ALL_DEGRADED: Headline = { status: 'degraded' };

/**
 * Runs the six metrics legs and reduces each to a {@link Headline}. Never throws:
 * a pass that fails as a whole degrades every headline (the cards are untouched).
 * A metrics `401` is the session's (cross-leg rule, § 2.4.4 D3) — surfaced as
 * `unauthorized` so the caller answers the same whole-session 401.
 */
export async function composeHeadlines(
  credentials: OverviewCredentials,
  fetchLeg: FetchLeg,
): Promise<HeadlineResult> {
  const all = (h: Headline) =>
    new Map<HeadlineDomain, Headline>(CARD_ORDER.map((d) => [d, h] as const));
  try {
    const legs = await operatorOverviewHeadlineLegs(credentials);
    const result = await compose(legs, {
      route: 'operator-overview',
      // Distinguishes these six lines from the card legs' in `console_composition_leg`.
      requestId: `${credentials.requestId}:headline`,
      fetchLeg,
    });
    if (result.unauthorized) return { unauthorized: true };
    const headlines = all(ALL_DEGRADED);
    for (const c of result.envelope.cards) {
      if (c.status === 'ok') headlines.set(c.domain, headlineFromMetricsBody(c.domain, c.data));
      else if (c.status === 'forbidden') headlines.set(c.domain, { status: 'forbidden' });
      else headlines.set(c.domain, ALL_DEGRADED);
    }
    return { unauthorized: false, headlines };
  } catch (err) {
    logger.warn('operator_overview_headline_failed', {
      requestId: credentials.requestId,
      cause: err instanceof Error ? err.name : 'unknown',
    });
    return { unauthorized: false, headlines: all(ALL_DEGRADED) };
  }
}

/**
 * Adds `headline` to each card. Every existing field of the envelope and of each
 * card is carried over untouched (AC-2: the old bytes are unchanged, a field is
 * added); card order and statuses are not read, let alone changed.
 */
export function withHeadlines(
  envelope: CompositionEnvelope,
  headlines: ReadonlyMap<HeadlineDomain, Headline>,
): CompositionEnvelope & { cards: Array<CompositionEnvelope['cards'][number] & { headline: Headline }> } {
  return {
    ...envelope,
    cards: envelope.cards.map((card) => ({
      ...card,
      headline: headlines.get(card.domain) ?? ALL_DEGRADED,
    })),
  };
}
