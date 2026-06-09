import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import { CARD_ORDER, type Card, type DomainKey } from '../api/types';
import type { DomainHealthState } from '../api/domain-health-api';
import { healthTone, type HealthTone } from '../lib/tone';

/**
 * TASK-PC-FE-061 — compact "도메인 상태 요약" band for the operator overview
 * (개요) home. Keeps the home one-click (the sidebar 개요 leaf is NOT turned
 * into a drill parent) while bridging to the full 도메인 상태 screen via a
 * "전체 보기 →" link to `/dashboards/health`.
 *
 * Reads the SAME `getDomainHealthState()` the `/dashboards/health` page uses
 * (the per-domain `/actuator/health` fan-out — tenant-agnostic infra liveness,
 * NOT a per-tenant data request). Resilient: a null `health` (bff unavailable /
 * unauthorized) collapses to a compact "불러올 수 없음" note WITHOUT blanking
 * the overview — the "전체 보기" link still works.
 */

const DOMAIN_LABEL: Record<DomainKey, string> = {
  iam: 'IAM',
  wms: 'WMS',
  scm: 'SCM',
  finance: 'Finance',
  erp: 'ERP',
};

const TONE_DOT: Record<HealthTone, string> = {
  healthy: 'bg-green-500',
  attention: 'bg-red-500',
  unknown: 'bg-muted-foreground/40',
};
const TONE_LABEL: Record<HealthTone, string> = {
  healthy: '정상',
  attention: '주의',
  unknown: '점검 불가',
};

export interface DomainHealthSummaryCardProps {
  state: DomainHealthState;
}

export function DomainHealthSummaryCard({
  state,
}: DomainHealthSummaryCardProps) {
  const cards = state.health?.cards ?? null;

  return (
    <section
      aria-labelledby="domain-health-summary-heading"
      data-testid="domain-health-summary"
      className="mt-8 rounded-lg border border-border bg-card p-5"
    >
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2
          id="domain-health-summary-heading"
          className="text-base font-medium text-foreground"
        >
          도메인 상태 요약
        </h2>
        <Link
          href="/dashboards/health"
          data-testid="domain-health-summary-viewall"
          className="shrink-0 text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          전체 보기 →
        </Link>
      </div>

      {cards === null ? (
        <p
          role="status"
          data-testid="domain-health-summary-unavailable"
          className="text-sm text-muted-foreground"
        >
          도메인 상태 요약을 일시적으로 불러올 수 없습니다. 전체 보기에서 다시
          시도하세요.
        </p>
      ) : (
        <SummaryBody cards={cards} />
      )}
    </section>
  );
}

function SummaryBody({ cards }: { cards: ReadonlyArray<Card> }) {
  const byDomain = new Map<DomainKey, Card>();
  for (const c of cards) byDomain.set(c.domain, c);

  let healthy = 0;
  let attention = 0;
  let unknown = 0;
  for (const c of cards) {
    const t = healthTone(c);
    if (t === 'healthy') healthy += 1;
    else if (t === 'attention') attention += 1;
    else unknown += 1;
  }

  return (
    <div className="flex flex-col gap-3">
      <p
        className="text-sm text-muted-foreground"
        data-testid="domain-health-summary-counts"
      >
        <span className="font-medium text-foreground">정상 {healthy}</span>
        {' · '}주의 {attention}
        {' · '}점검 불가 {unknown}{' '}
        <span className="text-xs">/ {cards.length}개 도메인</span>
      </p>
      <ul
        className="flex flex-wrap gap-2"
        data-testid="domain-health-summary-badges"
      >
        {CARD_ORDER.map((domain) => {
          const card = byDomain.get(domain);
          if (!card) return null;
          const tone = healthTone(card);
          return (
            <li
              key={domain}
              data-testid={`domain-health-summary-badge-${domain}`}
              data-tone={tone}
              className="inline-flex items-center gap-1.5 rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground"
            >
              <span
                className={cn('h-1.5 w-1.5 rounded-full', TONE_DOT[tone])}
                aria-hidden="true"
              />
              <span className="font-medium text-foreground">
                {DOMAIN_LABEL[domain]}
              </span>
              <span>{TONE_LABEL[tone]}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
