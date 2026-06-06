import {
  CARD_ORDER,
  type Card,
  type DomainKey,
  type OperatorOverview,
} from '../api/operator-overview-types';
import { DomainCard } from './DomainCard';
import { OverviewDegradeBanner } from './OverviewDegradeBanner';

/**
 * MVP "Operator Overview" cross-domain dashboard screen (TASK-PC-FE-011 —
 * ADR-MONO-017 § D8 Phase 7 MVP).
 *
 * Server component (architecture.md § Server vs Client Components;
 * AC-24). Receives the composed envelope from the page's SSR
 * `fetchOperatorOverview()` call and renders:
 *   - `<OverviewDegradeBanner>` if all 5 cards are non-`ok`
 *   - `<DomainCard>` × 5 in the FIXED `[gap, wms, scm, finance, erp]`
 *     order (a card-domain indexed map; defensive against any BE
 *     re-ordering — see comment below).
 *
 * Fixed-order invariant (§ 2.4.9.1 envelope schema): the BE always
 * returns the 5 cards in `[gap, wms, scm, finance, erp]` order;
 * `OperatorOverviewSchema` already asserts "exactly 5, each domain
 * exactly once". The screen builds a `Map<domain, Card>` and walks
 * `CARD_ORDER` so the DOM order is INDEPENDENT of the BE array
 * order — a future BE re-ordering bug cannot break the UI.
 */

export interface OperatorOverviewScreenProps {
  overview: OperatorOverview;
}

function indexByDomain(cards: ReadonlyArray<Card>): Map<DomainKey, Card> {
  const m = new Map<DomainKey, Card>();
  for (const c of cards) m.set(c.domain, c);
  return m;
}

export function OperatorOverviewScreen({
  overview,
}: OperatorOverviewScreenProps) {
  const byDomain = indexByDomain(overview.cards);

  return (
    <section aria-labelledby="operator-overview-heading">
      <header className="mb-6">
        <h1
          id="operator-overview-heading"
          className="mb-2 text-2xl font-semibold"
        >
          운영자 통합 개요
        </h1>
        <p className="text-sm text-muted-foreground">
          5개 도메인(IAM · WMS · SCM · Finance · ERP)의 상태를 한 번에
          확인합니다. 각 카드는 독립적으로 동작하며, 한 도메인의 일시
          장애가 다른 도메인이나 콘솔 전체에 영향을 주지 않습니다.
        </p>
        <p
          className="mt-1 text-xs text-muted-foreground"
          data-testid="operator-overview-asof"
        >
          기준 시각: {overview.asOf} (UTC)
        </p>
      </header>

      <OverviewDegradeBanner initial={overview} />

      <div
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        data-testid="operator-overview-cards"
      >
        {CARD_ORDER.map((domain) => {
          const card = byDomain.get(domain);
          // The schema-level refinement guarantees presence; this is a
          // belt-and-braces fallback (never crash on a synthetic test
          // that bypasses the schema).
          if (!card) return null;
          return (
            <DomainCard
              key={domain}
              card={card}
              overviewForRetry={overview}
            />
          );
        })}
      </div>
    </section>
  );
}
