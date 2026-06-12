import type { ReactNode } from 'react';
import {
  type Card,
  type DomainKey,
  type DomainHealth,
  type DegradedReason,
  type HealthStatus,
} from '../api/types';
import { RetryButton } from './RetryButton';

/**
 * Per-domain card (TASK-PC-FE-013). Server component. Renders one of
 * two top-level branches per `card.status`:
 *
 *  - `ok` — sub-branches on `data.status` (Spring Boot health enum):
 *    - `UP` → green-checkmark visual ("정상").
 *    - `DOWN` → red-cross visual ("심각 (자가 보고)") — producer
 *      self-reported critical; NOT degraded (the BFF reached the
 *      producer; the producer is honestly reporting itself as down).
 *    - `OUT_OF_SERVICE` → yellow-wrench visual ("점검 중").
 *    - `UNKNOWN` → grey-question visual ("상태 불명").
 *  - `degraded` — "leg unreachable" placeholder + `<RetryButton>`.
 *    Reason ∈ { DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN } surfaced as
 *    user-friendly Korean copy.
 *
 * `forbidden` is NEVER expected on this route (§ 2.4.9.2 invariant +
 * `CardSchema` rejects it at parse time); the discriminated union has
 * no `forbidden` arm.
 */

const DOMAIN_TITLE: Record<DomainKey, string> = {
  iam: 'IAM',
  wms: 'WMS',
  scm: 'SCM',
  finance: 'Finance',
  erp: 'ERP',
  ecommerce: 'E-Commerce',
};

const DEGRADED_COPY: Record<DegradedReason, string> = {
  DOWNSTREAM_ERROR: '하위 서비스에서 오류가 발생했습니다.',
  TIMEOUT: '응답 시간이 초과되었습니다.',
  CIRCUIT_OPEN: '서비스가 일시적으로 응답할 수 없습니다.',
};

interface HealthVisual {
  /** ASCII glyph used in the card (kept ASCII-only — no emojis per project preference). */
  glyph: string;
  /** Korean copy describing the producer's self-reported health state. */
  label: string;
  /** Tailwind classes for the visual accent (background + text). */
  accent: string;
}

const HEALTH_VISUAL: Record<HealthStatus, HealthVisual> = {
  UP: {
    glyph: 'OK',
    label: '정상',
    accent: 'bg-green-100 text-green-900 dark:bg-green-900/30 dark:text-green-200',
  },
  DOWN: {
    glyph: 'X',
    label: '심각 (자가 보고)',
    accent: 'bg-red-100 text-red-900 dark:bg-red-900/30 dark:text-red-200',
  },
  OUT_OF_SERVICE: {
    glyph: '!',
    label: '점검 중',
    accent: 'bg-yellow-100 text-yellow-900 dark:bg-yellow-900/30 dark:text-yellow-200',
  },
  UNKNOWN: {
    glyph: '?',
    label: '상태 불명',
    accent: 'bg-gray-100 text-gray-900 dark:bg-gray-800 dark:text-gray-200',
  },
};

function OkBody({ card }: { card: Card & { status: 'ok' } }): ReactNode {
  const status = card.data.status;
  const visual = HEALTH_VISUAL[status];
  return (
    <div className="space-y-3">
      <div
        data-testid={`domain-health-card-${card.domain}-visual`}
        data-health-status={status}
        className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-semibold ${visual.accent}`}
      >
        <span
          aria-hidden="true"
          data-testid={`domain-health-card-${card.domain}-glyph`}
          className="font-mono text-base"
        >
          {visual.glyph}
        </span>
        <span data-testid={`domain-health-card-${card.domain}-label`}>
          {visual.label}
        </span>
      </div>
      <p
        className="text-xs text-muted-foreground"
        data-testid={`domain-health-card-${card.domain}-raw-status`}
      >
        actuator status: {status}
      </p>
    </div>
  );
}

export interface DomainHealthCardProps {
  card: Card;
  /** Seeds the per-card retry button (only relevant on `degraded`). */
  healthForRetry: DomainHealth;
}

export function DomainHealthCard({ card, healthForRetry }: DomainHealthCardProps) {
  const id = `domain-health-card-${card.domain}`;
  return (
    <section
      aria-labelledby={`${id}-heading`}
      data-testid={`domain-health-card-${card.domain}`}
      data-domain={card.domain}
      data-status={card.status}
      className="flex flex-col rounded-lg border border-border bg-background p-5"
    >
      <h2
        id={`${id}-heading`}
        className="mb-3 text-lg font-semibold text-foreground"
      >
        {DOMAIN_TITLE[card.domain]}
      </h2>

      <div className="flex-1">
        {card.status === 'ok' && <OkBody card={card} />}

        {card.status === 'degraded' && (
          <div
            role="status"
            data-testid={`domain-health-card-${card.domain}-degraded`}
            className="space-y-3"
          >
            <p className="text-sm text-muted-foreground">
              {DEGRADED_COPY[card.reason]}
            </p>
            <p
              className="text-xs text-muted-foreground"
              data-testid={`domain-health-card-${card.domain}-degraded-reason`}
            >
              사유: {card.reason}
            </p>
            <RetryButton
              initial={healthForRetry}
              label="다시 시도"
              testidSuffix={`${card.domain}-degraded`}
            />
          </div>
        )}
      </div>
    </section>
  );
}
