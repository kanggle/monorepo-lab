import Link from 'next/link';
import {
  type Card,
  type DomainKey,
  type OperatorOverview,
} from '../api/operator-overview-types';
import { OkSummary } from './DomainCardSummaries';
import { DegradedState, ForbiddenState } from './DomainCardStates';

/**
 * Per-domain card (TASK-PC-FE-011). Server component. Renders one of
 * three branches per `card.status`:
 *
 *  - `ok` — domain-specific summary using `data` (count / snapshot /
 *    balance available) via {@link OkSummary} (defensive `*DataSchema`
 *    narrowing; a parse miss falls through to a "summary unavailable"
 *    placeholder — the card stays `ok` semantically; never a UI crash).
 *  - `degraded` — {@link DegradedState}: "data unavailable" placeholder +
 *    `<RetryButton>`.
 *  - `forbidden` — {@link ForbiddenState}: "not available to your role /
 *    tenant" placeholder (+ finance `MISSING_PREREQUISITE` hint).
 *
 * TASK-PC-FE-212: the `ok`-branch per-domain summaries ({@link OkSummary}) and
 * the non-`ok` state blocks ({@link DegradedState}/{@link ForbiddenState}) live
 * in sibling files; this shell keeps the title/drill-down + status dispatch
 * (behavior-preserving split).
 */

const DOMAIN_TITLE: Record<DomainKey, string> = {
  iam: 'IAM 계정',
  wms: 'WMS 재고',
  scm: 'SCM 가시성',
  finance: 'Finance 잔액',
  erp: 'ERP 마스터',
  ecommerce: 'ecommerce 상품',
};

// -------------------------------------------------------------------------
// Card shell — renders title + status-specific body. Server component.
// -------------------------------------------------------------------------

export interface DomainCardProps {
  card: Card;
  /** Seeds the per-card retry button (only relevant on `degraded`). */
  overviewForRetry: OperatorOverview;
}

export function DomainCard({ card, overviewForRetry }: DomainCardProps) {
  const id = `domain-card-${card.domain}`;

  // GAP-card drill-down (TASK-PC-FE-034 / AC-5 + AC-6): on the home
  // cross-domain overview the IAM card links to the GAP-only composed
  // overview detail (`/dashboards`, § 2.4.4 / ADR-MONO-015 D1-B —
  // accounts · audit · operators 3-leg). AC-6 default: the affordance is
  // present ONLY when the IAM card is `ok` — on `degraded` / `forbidden`
  // the IAM detail would itself degrade, so the link is suppressed and the
  // card keeps its existing placeholder + retry behaviour. The other 4
  // domain cards are unchanged (no drill-down — a separate future task).
  const gapDrilldown = card.domain === 'iam' && card.status === 'ok';

  return (
    <section
      aria-labelledby={`${id}-heading`}
      data-testid={`operator-overview-card-${card.domain}`}
      data-domain={card.domain}
      data-status={card.status}
      className="flex flex-col rounded-lg border border-border bg-background p-5"
    >
      <h2
        id={`${id}-heading`}
        className="mb-3 text-lg font-semibold text-foreground"
      >
        {gapDrilldown ? (
          <Link
            href="/dashboards"
            data-testid="operator-overview-card-iam-drilldown"
            className="inline-flex items-center gap-1 rounded underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {DOMAIN_TITLE[card.domain]}
            <span aria-hidden="true">→</span>
            <span className="sr-only">상세 보기</span>
          </Link>
        ) : (
          DOMAIN_TITLE[card.domain]
        )}
      </h2>

      <div className="flex-1">
        {card.status === 'ok' && <OkSummary card={card} />}

        {card.status === 'degraded' && (
          <DegradedState card={card} overviewForRetry={overviewForRetry} />
        )}

        {card.status === 'forbidden' && <ForbiddenState card={card} />}
      </div>
    </section>
  );
}
