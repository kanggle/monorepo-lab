import {
  CARD_ORDER,
  type Card,
  type DomainKey,
  type DomainHealth,
} from '../api/types';
import { DomainHealthCard } from './DomainHealthCard';
import { DegradeBanner } from './DegradeBanner';

/**
 * Phase 7 "Domain Health Overview" cross-domain dashboard screen
 * (TASK-PC-FE-013 — `console-integration-contract.md` § 2.4.9.2).
 *
 * Server component. Receives the composed envelope from the page's
 * SSR `fetchDomainHealth()` call and renders:
 *   - `<DegradeBanner>` if all 5 cards are `degraded`
 *   - `<DomainHealthCard>` × 5 in the FIXED `[gap, wms, scm, finance, erp]`
 *     order (indexed by domain; defensive against any BE re-ordering).
 *
 * Fixed-order invariant (§ 2.4.9.2 envelope schema): the BE always
 * returns the 5 cards in `[gap, wms, scm, finance, erp]` order;
 * `DomainHealthSchema` asserts "exactly 5, each domain exactly once".
 * The screen builds a `Map<domain, Card>` and walks `CARD_ORDER` so the
 * DOM order is INDEPENDENT of the BE array order — a future BE
 * re-ordering bug cannot break the UI. The DOM order is ALSO independent
 * of `status` (cards never reordered by status).
 */

export interface DomainHealthScreenProps {
  health: DomainHealth;
}

function indexByDomain(cards: ReadonlyArray<Card>): Map<DomainKey, Card> {
  const m = new Map<DomainKey, Card>();
  for (const c of cards) m.set(c.domain, c);
  return m;
}

export function DomainHealthScreen({ health }: DomainHealthScreenProps) {
  const byDomain = indexByDomain(health.cards);

  return (
    <section aria-labelledby="domain-health-heading">
      <header className="mb-6">
        <h1 id="domain-health-heading" className="mb-2 text-2xl font-semibold">
          도메인 상태 개요
        </h1>
        <p className="text-sm text-muted-foreground">
          5개 도메인(IAM · WMS · SCM · Finance · ERP)의 자체 헬스 상태를
          한 번에 확인합니다. 각 도메인 카드는 독립적으로 동작하며, 한
          도메인의 일시 장애가 다른 도메인이나 콘솔 전체에 영향을 주지
          않습니다.
        </p>
        <p
          className="mt-1 text-xs text-muted-foreground"
          data-testid="domain-health-asof"
        >
          기준 시각: {health.asOf} (UTC)
        </p>
      </header>

      <DegradeBanner initial={health} />

      <div
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        data-testid="domain-health-cards"
      >
        {CARD_ORDER.map((domain) => {
          const card = byDomain.get(domain);
          if (!card) return null;
          return (
            <DomainHealthCard
              key={domain}
              card={card}
              healthForRetry={health}
            />
          );
        })}
      </div>
    </section>
  );
}
