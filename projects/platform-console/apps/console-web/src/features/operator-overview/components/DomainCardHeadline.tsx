import {
  PERIOD_LABEL,
  comparisonBars,
  describeDelta,
  formatDelta,
  formatMetricValue,
  metricDelta,
} from '@/shared/lib/performance-format';
import { headlineLabel, type Headline } from '@/shared/lib/operator-overview-headline';
import type { DomainKey } from '../api/operator-overview-types';

/**
 * The card's headline performance metric (ADR-MONO-084 D7, TASK-PC-FE-368):
 * this week's value + the change against the previous same-length window.
 * Server component, read-only. Rendered BELOW the card's own state body on every
 * card status — the headline is its own cell, so a degraded card can still show
 * a healthy headline and the reverse.
 *
 * Failure is said in words and is never a 0 (a 0 would read as «nothing
 * happened»): `degraded` → «실적을 불러올 수 없음», `forbidden` → «권한 없음».
 * An absent `headline` (a payload from before the field) renders nothing.
 *
 * Direction is never colour-coded: a rise is good for revenue and bad for failed
 * logins, and the card does not know which (same rule as the domain strips).
 */
/**
 * «이번 주 · 지난 주» on one scale for ONE metric (TASK-PC-FE-379). Decorative
 * (`aria-hidden`) — the values are already text above. Both bars are the same
 * neutral hue (current solid, previous light): the bars show «more or less»,
 * never «better or worse». The scale never spans metrics or cards (their units
 * differ).
 */
function WeekComparisonBars({
  testid,
  current,
  previous,
}: {
  testid: string;
  current: string;
  previous: string;
}) {
  const bars = comparisonBars(current, previous);
  const rows = [
    { label: '이번 주', percent: bars.currentPercent, tone: 'bg-primary/80', key: 'current' },
    { label: '지난 주', percent: bars.previousPercent, tone: 'bg-primary/30', key: 'previous' },
  ] as const;
  return (
    <div aria-hidden="true" data-testid={testid} className="mt-1 space-y-1">
      {rows.map((r) => (
        <div key={r.key} className="flex items-center gap-2">
          <span className="w-10 shrink-0 text-[10px] text-muted-foreground">{r.label}</span>
          <div className="h-1.5 flex-1 rounded bg-muted">
            <div
              data-testid={`${testid}-${r.key}`}
              className={`h-1.5 rounded ${r.tone}`}
              style={{ width: `${r.percent}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

export function DomainCardHeadline({
  domain,
  headline,
}: {
  domain: DomainKey;
  headline: Headline | undefined;
}) {
  if (!headline) return null;
  const testid = `operator-overview-card-${domain}-headline`;

  if (headline.status !== 'ok') {
    return (
      <p
        role="status"
        data-testid={`${testid}-${headline.status}`}
        className="mt-3 border-t border-border pt-3 text-xs text-muted-foreground"
      >
        {headline.status === 'forbidden'
          ? '실적을 볼 권한이 없습니다.'
          : '실적을 불러올 수 없습니다.'}
      </p>
    );
  }

  return (
    <div data-testid={testid} className="mt-3 space-y-2 border-t border-border pt-3">
      {headline.metrics.map((m) => {
        const delta = metricDelta(m.current, m.previous);
        return (
          <div key={m.key} data-testid={`${testid}-${m.key}`}>
            <p className="text-xs text-muted-foreground">
              {PERIOD_LABEL[headline.period]} {headlineLabel(domain, m)}
            </p>
            <p
              data-testid={`${testid}-${m.key}-value`}
              className="text-lg font-semibold tabular-nums text-foreground"
            >
              {formatMetricValue(m.current, m)}
            </p>
            <p className="text-xs tabular-nums text-muted-foreground">
              <span aria-hidden="true" data-testid={`${testid}-${m.key}-delta`}>
                {formatDelta(delta)}
              </span>
              <span className="sr-only">{describeDelta(delta)}</span>
              <span className="ml-2">직전 {formatMetricValue(m.previous, m)}</span>
            </p>
            <WeekComparisonBars testid={`${testid}-${m.key}-bars`} current={m.current} previous={m.previous} />
          </div>
        );
      })}
    </div>
  );
}
