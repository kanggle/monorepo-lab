import {
  PERIOD_LABEL,
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
          </div>
        );
      })}
    </div>
  );
}
